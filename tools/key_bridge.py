"""키보드 연결 프로그램: PC 스페이스바를 Virtual Console REST로 보내 브레이크 페달 신호로 바꾼다.

  스페이스바 누름 → PUT /vehicle/controls?brake=100     뗌 → brake=0

실행 전: `adb forward tcp:8088 tcp:8088`. 이 프로그램이 1초마다 `POST /browser/session`을 보내
Virtual Console 웹페이지를 열지 않아도 제어된다(웹페이지를 이미 열어 두었다면 --no-heartbeat).
Windows 전용(GetAsyncKeyState 폴링). 상태 기반이라 키를 누르고 있을 때의 반복 입력은 자동으로 무시된다.
종료: Ctrl+C (종료하면 브레이크를 0으로 되돌린다).
"""
import argparse
import ctypes
import queue
import threading
import time
import urllib.error
import urllib.request

VK_SPACE = 0x20
user32 = ctypes.windll.user32


def is_down(vk):
    return bool(user32.GetAsyncKeyState(vk) & 0x8000)


class Sender(threading.Thread):
    """PUT을 순서대로 보내는 스레드. 폴링 루프가 네트워크 때문에 막히지 않게 한다."""

    def __init__(self, base):
        super().__init__(daemon=True)
        self.base = base
        self.q = queue.Queue()

    def send(self, path):
        self.q.put(path)

    def run(self):
        while True:
            path = self.q.get()
            try:
                t0 = time.perf_counter()
                urllib.request.urlopen(urllib.request.Request(self.base + path, method="PUT"), timeout=3).read()
                print(f"  PUT {path}  ({(time.perf_counter() - t0) * 1000:.0f}ms)")
            except urllib.error.HTTPError as e:
                print(f"  PUT {path} 실패: HTTP {e.code} (503이면 Virtual Console 연결이 없는 것)")
            except Exception as e:  # noqa: BLE001
                print(f"  PUT {path} 실패: {e}  (adb forward tcp:8088 tcp:8088 확인)")


def heartbeat(base):
    while True:
        try:
            urllib.request.urlopen(urllib.request.Request(base + "/browser/session?client=pedalcheck-key", method="POST"), timeout=2).read()
        except Exception:  # noqa: BLE001
            pass
        time.sleep(1)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base", default="http://127.0.0.1:8088")
    ap.add_argument("--no-heartbeat", action="store_true", help="Virtual Console 웹페이지를 이미 열어 둔 경우")
    a = ap.parse_args()

    if not a.no_heartbeat:
        threading.Thread(target=heartbeat, args=(a.base,), daemon=True).start()
    sender = Sender(a.base)
    sender.start()
    prev = False
    print("키보드 연결 중. 스페이스바 = 브레이크. 종료: Ctrl+C")
    try:
        while True:
            cur = is_down(VK_SPACE)
            if cur != prev:
                print("브레이크", "누름" if cur else "뗌")
                sender.send(f"/vehicle/controls?brake={100 if cur else 0}")
                prev = cur
            time.sleep(0.004)
    except KeyboardInterrupt:
        print("\n종료: 브레이크를 0으로 되돌립니다.")
        sender.send("/vehicle/controls?brake=0")
        time.sleep(0.6)


if __name__ == "__main__":
    main()
