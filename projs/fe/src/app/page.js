"use client";

import { useState, useEffect, useCallback } from "react";
import KakaoMap from "@/components/KakaoMap";

/**
 * 메인 페이지
 * 1. 환경변수 NEXT_PUBLIC_KAKAO_JS_KEY로 SDK를 즉시 로드한다 (백엔드 불필요)
 * 2. 환경변수가 없으면 /api/config에서 fallback 조회한다
 * 3. SDK 로드 완료 후 KakaoMap 컴포넌트를 렌더링한다
 */
export default function Home() {
  // SDK 로드 상태: "loading" | "ready" | "error"
  const [sdkStatus, setSdkStatus] = useState("loading");
  const [errorMessage, setErrorMessage] = useState("");

  /**
   * Kakao Maps SDK 스크립트를 동적으로 생성하여 로드한다.
   */
  const loadKakaoSDK = useCallback((jsKey) => {
    return new Promise((resolve, reject) => {
      // 이미 로드된 경우 스킵
      if (window.kakao && window.kakao.maps) {
        window.kakao.maps.load(() => resolve());
        return;
      }

      const script = document.createElement("script");
      script.src = `//dapi.kakao.com/v2/maps/sdk.js?appkey=${jsKey}&autoload=false`;
      script.async = true;

      script.onload = () => {
        window.kakao.maps.load(() => resolve());
      };

      script.onerror = () => {
        reject(new Error("Kakao Maps SDK 스크립트 로드에 실패했습니다."));
      };

      document.head.appendChild(script);
    });
  }, []);

  /**
   * 초기화: 환경변수에서 키를 가져오고, 없으면 백엔드 fallback
   */
  useEffect(() => {
    let cancelled = false;

    async function init() {
      try {
        // 1. 환경변수에서 직접 키 가져오기 (백엔드 없이 즉시 로드)
        let jsKey = process.env.NEXT_PUBLIC_KAKAO_JS_KEY;

        // 2. 환경변수가 없으면 백엔드 fallback (3초 타임아웃)
        if (!jsKey) {
          try {
            const { fetchConfig } = await import("@/lib/api");
            const controller = new AbortController();
            const timeout = setTimeout(() => controller.abort(), 3000);
            const config = await fetchConfig(controller.signal);
            clearTimeout(timeout);
            jsKey = config.kakaoJsKey;
          } catch {
            // 백엔드 실패 시 에러
          }
        }

        if (cancelled) return;

        if (!jsKey) {
          throw new Error(
            "Kakao JavaScript 키가 설정되지 않았습니다. .env.local에 NEXT_PUBLIC_KAKAO_JS_KEY를 설정하세요."
          );
        }

        // 3. SDK 동적 로드
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

  // 로딩 상태
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

  // 에러 상태
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

  // SDK 로드 완료: 지도 렌더링
  return (
    <main>
      <KakaoMap />
    </main>
  );
}
