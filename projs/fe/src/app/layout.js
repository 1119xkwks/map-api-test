import "./globals.css";

export const metadata = {
  title: "아파트 실거래가 지도",
  description: "카카오맵 기반 아파트 매매 실거래가 조회 서비스",
};

export default function RootLayout({ children }) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
