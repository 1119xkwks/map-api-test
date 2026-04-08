"use client";

/**
 * 줌 레벨 안내 메시지 컴포넌트
 * 지도를 충분히 확대하지 않았을 때 상단 중앙에 반투명 배너로 안내 문구를 표시한다.
 *
 * @param {{ show: boolean }} props
 */
export default function ZoomMessage({ show }) {
  if (!show) return null;

  return (
    <div className="zoom-message" role="status" aria-live="polite">
      지도를 더 확대하면 아파트 실거래가를 확인할 수 있습니다.
    </div>
  );
}
