"use client";

import { useState, useRef, useCallback } from "react";
import { fetchApartments } from "@/lib/api";

/**
 * 아파트 실거래가 데이터 fetch 훅
 * - bounds 기반 API 호출
 * - AbortController로 이전 요청 자동 취소 (중복 방지)
 * - 로딩/에러/데이터 상태 관리
 */
export function useApartments() {
  const [apartments, setApartments] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  // 이전 요청 취소를 위한 AbortController 참조
  const abortControllerRef = useRef(null);

  /**
   * 지도 bounds 범위의 아파트 데이터를 조회한다.
   * @param {{ swLat: number, swLng: number, neLat: number, neLng: number }} bounds
   */
  const loadApartments = useCallback(async (bounds) => {
    // 이전 요청이 있으면 취소
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }

    // 새 AbortController 생성
    const controller = new AbortController();
    abortControllerRef.current = controller;

    setIsLoading(true);
    setError(null);

    try {
      const result = await fetchApartments(bounds, controller.signal);

      // 요청이 취소되지 않았을 때만 상태 업데이트
      if (!controller.signal.aborted) {
        setApartments(result.data || []);
        setIsLoading(false);
      }
    } catch (err) {
      // AbortError는 무시 (의도적 취소)
      if (err.name === "AbortError") {
        return;
      }
      if (!controller.signal.aborted) {
        setError(err.message || "데이터 조회에 실패했습니다.");
        setApartments([]);
        setIsLoading(false);
      }
    }
  }, []);

  /**
   * 데이터 초기화 (줌 레벨 변경 등으로 마커 제거 시)
   */
  const clearApartments = useCallback(() => {
    // 진행 중인 요청 취소
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    setApartments([]);
    setError(null);
    setIsLoading(false);
  }, []);

  return {
    apartments,
    isLoading,
    error,
    loadApartments,
    clearApartments,
  };
}
