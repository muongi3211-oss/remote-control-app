import socket
import struct
import threading
import mss
import cv2
import numpy as np
import pyautogui

# Tắt tính năng ngắt khẩn cấp khi di chuột ra góc màn hình của pyautogui
pyautogui.FAILSAFE = False

def receive_commands(conn):
    """Luồng con nhận lệnh chuột và bàn phím từ điện thoại"""
    while True:
        try:
            data = conn.recv(1024)
            if not data:
                break
            command = data.decode('utf-8').strip()
            parts = command.split(':', 1)
            
            if len(parts) >= 2:
                action, value = parts[0], parts[1]
                
                if action in ["MOVE", "CLICK", "RIGHT_CLICK"]:
                    coords = value.split(':')
                    if len(coords) == 2:
                        x, y = float(coords[0]), float(coords[1])
                        if action == "MOVE":
                            pyautogui.moveTo(x, y)
                        elif action == "CLICK":
                            pyautogui.click(x, y)
                        elif action == "RIGHT_CLICK":
                            pyautogui.moveTo(x, y)
                            pyautogui.rightClick()
                elif action == "KEY":
                    pyautogui.write(value, interval=0.02)
                elif action == "SPECIAL":
                    pyautogui.press(value)
        except Exception as e:
            print(f"Lỗi nhận lệnh: {e}")
            break

def handle_client(conn, addr):
    print(f"Điện thoại đã kết nối: {addr}")
    
    cmd_thread = threading.Thread(target=receive_commands, args=(conn,), daemon=True)
    cmd_thread.start()

    # Tỷ lệ scale hình ảnh (0.8 giúp cực kỳ sắc nét nhưng vẫn mượt)
    scale_factor = 0.8

    with mss.mss() as sct:
        monitor = sct.monitors[1]
        
        while True:
            try:
                # 1. Chụp màn hình
                img = sct.grab(monitor)
                frame = np.array(img)
                frame = cv2.cvtColor(frame, cv2.COLOR_BGRA2BGR)
                
                # 2. Lấy vị trí chuột thực tế trên PC và vẽ con trỏ lên frame
                mx, my = pyautogui.position()
                mx_scaled = int(mx * scale_factor)
                my_scaled = int(my * scale_factor)
                
                # Vẽ con trỏ chuột (chấm tròn đỏ viền trắng nổi bật)
                cv2.circle(frame, (mx_scaled, my_scaled), 10, (255, 255, 255), -1) # Viền trắng
                cv2.circle(frame, (mx_scaled, my_scaled), 7, (0, 0, 255), -1)    # Tâm đỏ
                
                # 3. Resize với tỷ lệ 0.8 để nét hơn
                frame = cv2.resize(frame, (0, 0), fx=scale_factor, fy=scale_factor)
                
                # 4. Nén JPEG chất lượng cao (85%) để sắc nét chữ và icon
                encode_param = [int(cv2.IMWRITE_JPEG_QUALITY), 85]
                _, encimg = cv2.imencode('.jpg', frame, encode_param)
                img_bytes = encimg.tobytes()
                
                # 5. Gửi dữ liệu qua mạng
                conn.sendall(struct.pack(">L", len(img_bytes)) + img_bytes)
                
            except Exception as e:
                print(f"Mất kết nối stream: {e}")
                break
                
    conn.close()

def run_server():
    HOST = '0.0.0.0'
    PORT = 5000
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.bind((HOST, PORT))
    server_socket.listen(1)
    print(f"Server đang chạy tại cổng {PORT}...")
    
    while True:
        conn, addr = server_socket.accept()
        handle_client(conn, addr)

if __name__ == "__main__":
    run_server()
