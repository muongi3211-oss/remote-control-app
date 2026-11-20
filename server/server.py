import socket
import struct
import time
import mss
import cv2
import numpy as np

HOST = '0.0.0.0'
PORT = 5555
TARGET_FPS = 60


def main():
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_socket.bind((HOST, PORT))
    server_socket.listen(1)
    print(f"PC Server đang lắng nghe tại {HOST}:{PORT}...")

    frame_interval = 1.0 / TARGET_FPS

    while True:
        conn, addr = server_socket.accept()
        conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        print(f"Đã kết nối từ: {addr}")

        try:
            with mss.mss() as sct:
                monitor = sct.monitors[1] if len(sct.monitors) > 1 else sct.monitors[0]
                while True:
                    start_t = time.perf_counter()

                    img = sct.grab(monitor)
                    frame = np.array(img)
                    if frame.size == 0:
                        continue

                    frame_bgr = cv2.cvtColor(frame, cv2.COLOR_BGRA2BGR)
                    success, encoded = cv2.imencode(
                        '.jpg',
                        frame_bgr,
                        [int(cv2.IMWRITE_JPEG_QUALITY), 75]
                    )
                    if not success:
                        continue

                    data = encoded.tobytes()
                    header = struct.pack(">I", len(data))
                    conn.sendall(header + data)

                    elapsed = time.perf_counter() - start_t
                    if elapsed < frame_interval:
                        time.sleep(frame_interval - elapsed)

        except (ConnectionResetError, BrokenPipeError, OSError) as e:
            print(f"Ngắt kết nối ({e}). Đang chờ kết nối mới...")
        finally:
            try:
                conn.close()
            except OSError:
                pass


if __name__ == '__main__':
    main()
