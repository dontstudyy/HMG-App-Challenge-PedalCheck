"""시연용 과거 기록 생성: 앱 설정의 '시연용 기록 넣기'가 읽는 JSON(`demo_history.json`)을 만든다.

  python tools/seed_history.py                 # 기준선 600ms
  python tools/seed_history.py --baseline 374  # 시연 PC에서 키보드로 직접 잰 중앙값을 넣는다

구성 (설계서 9장, 오늘 기준 상대 월이라 어느 달에 시연해도 마지막 달이 이번 달이 된다)
  2달 전: 기준선 기간 2주(1~14일) 세션 10개 — 중앙값 = 기준선
  1달 전: 세션 6개 — 중앙값 = 기준선 +1.7%
  이번 달: 세션 5개 — 중앙값 = 기준선 +3.3%   → 월별 값이 매달 커져 3개월 안내가 뜬다

세션마다 값은 중앙값 둘레에 흩어 놓고, 각 달의 중앙값이 정확히 목표값이 되도록 짝수 개는 가운데 두 값이 목표 ±같은 크기가 되게 한다.
앱이 이 기록을 '시연용'으로 표시한다. 오늘 측정은 진짜 값이다.
"""
import argparse
import json
import pathlib
import statistics

# 월별 세션 구성: (몇 달 전, 일 목록, 목표 대비 흩어짐[ms], 목표 배율)
PLAN = [
    (2, [1, 2, 4, 5, 7, 8, 10, 11, 13, 14], [3, -5, 8, -1, -8, 1, 12, -3, 5, -12], 1.000),
    (1, [3, 8, 12, 17, 22, 27], [4, -9, 1, 9, -4, -1], 1.017),
    (0, [1, 3, 5, 7, 9], [3, -8, 0, 8, -3], 1.033),
]
HOURS = [7, 8, 9, 17, 18]


def build(baseline: int) -> dict:
    sessions = []
    medians = []
    for months_ago, days, offsets, ratio in PLAN:
        target = round(baseline * ratio)
        values = [target + o for o in offsets]
        assert statistics.median(values) == target, (months_ago, values)
        medians.append(target)
        for i, (day, v) in enumerate(zip(days, values)):
            sessions.append({
                "monthsAgo": months_ago, "day": day,
                "hour": HOURS[i % len(HOURS)], "minute": 5 + (i * 7) % 50,
                "meanMs": v,
            })
    assert medians[0] < medians[1] < medians[2], f"월별 값이 매달 커져야 합니다: {medians}"
    return {"baselineMs": baseline, "sessions": sessions}


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--baseline", type=int, default=600, help="기준선(ms). 기본 600")
    ap.add_argument("--out", default=str(pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/demo_history.json"))
    a = ap.parse_args()
    data = build(a.baseline)
    out = pathlib.Path(a.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    by_month = {}
    for s in data["sessions"]:
        by_month.setdefault(s["monthsAgo"], []).append(s["meanMs"])
    print(f"기준선 {a.baseline}ms → {out}")
    for m in sorted(by_month, reverse=True):
        label = {2: "2달 전", 1: "1달 전", 0: "이번 달"}[m]
        print(f"  {label}: 세션 {len(by_month[m])}개, 중앙값 {statistics.median(by_month[m]):.0f}ms")


if __name__ == "__main__":
    main()
