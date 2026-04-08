"use client";

import { useState, useEffect, useCallback } from "react";
import KakaoMap from "@/components/KakaoMap";

/**
 * 메인 페이지
 * 1. 환경변수 NEXT_PUBLIC_KAKAO_JS_KEY로 SDK를 즉시 로드 (백엔드 불필요)
 * 2. SDK 로드 완료 후 KakaoMap 컴포넌트를 렌더링
 */
export default function Home() {
  const [sdkStatus, setSdkStatus] = useState("loading");
  const [errorMessage, setErrorMessage] = useState("");

  /** Kakao Maps SDK 스크립트를 동적으로 로드한다 */
  const loadKakaoSDK = useCallback((jsKey) => {
    return new Promise((resolve, reject) => {
      if (window.kakao && window.kakao.maps) {
        window.kakao.maps.load(() => resolve());
        return;
      }

      const script = document.createElement("script");
      script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${jsKey}&autoload=false`;
      script.async = true;

      script.onload = () => {
        window.kakao.maps.load(() => resolve());
      };
      script.onerror = () => {
        reject(new Error("Kakao Maps SDK 로드 실패. 키와 도메인 설정을 확인하세요."));
      };

      document.head.appendChild(script);
    });
  }, []);

  /** 초기화: 환경변수에서 키 로드 → SDK 로드 */
  useEffect(() => {
    let cancelled = false;

    async function init() {
      try {
        const jsKey = process.env.NEXT_PUBLIC_KAKAO_JS_KEY;

        if (!jsKey) {
          throw new Error(".env.local에 NEXT_PUBLIC_KAKAO_JS_KEY를 설정하세요.");
        }

        await loadKakaoSDK(jsKey);

        if (cancelled) return;
        setSdkStatus("ready");
      } catch (err) {
        if (cancelled) return;
        setErrorMessage(err.message || "초기화에 실패했습니다.");
        setSdkStatus("error");
      }
    }

    init();
    return () => { cancelled = true; };
  }, [loadKakaoSDK]);

  if (sdkStatus === "loading") {
    return (
      <main>
        <div className="page-loading">
          <div className="loading-spinner" />
          <span className="page-loading-text">지도를 불러오는 중...</span>
        </div>
      </main>
    );
  }

  if (sdkStatus === "error") {
    return (
      <main>
        <div className="page-error">
          <span className="page-error-text">지도를 불러올 수 없습니다</span>
          <span className="page-error-detail">{errorMessage}</span>
        </div>
      </main>
    );
  }

  return (
    <main>
      <KakaoMap />
    </main>
  );
}
