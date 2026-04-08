package com.mapapi.util;

import com.mapapi.dto.TradeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 국토교통부 아파트 매매 실거래가 상세 자료 API의 XML 응답을 파싱하는 유틸리티.
 *
 * XML 구조:
 * <response>
 *   <header>
 *     <resultCode>00</resultCode>
 *     <resultMsg>NORMAL SERVICE.</resultMsg>
 *   </header>
 *   <body>
 *     <items>
 *       <item>
 *         <aptNm>래미안아파트</aptNm>
 *         <dealAmount> 95,000</dealAmount>
 *         ...
 *       </item>
 *     </items>
 *   </body>
 * </response>
 */
public class XmlParserUtil {

    private static final Logger log = LoggerFactory.getLogger(XmlParserUtil.class);

    private XmlParserUtil() {
        // 유틸리티 클래스 — 인스턴스 생성 방지
    }

    /**
     * 공공API XML 응답 문자열을 파싱하여 TradeItem 리스트로 변환한다.
     *
     * @param xml XML 응답 문자열
     * @return TradeItem 리스트
     */
    public static List<TradeItem> parseTradeXml(String xml) {
        List<TradeItem> items = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // XXE 공격 방지
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            // 응답 코드 확인
            NodeList resultCodeNodes = doc.getElementsByTagName("resultCode");
            if (resultCodeNodes.getLength() > 0) {
                String resultCode = resultCodeNodes.item(0).getTextContent();
                if (!"00".equals(resultCode)) {
                    String resultMsg = "";
                    NodeList msgNodes = doc.getElementsByTagName("resultMsg");
                    if (msgNodes.getLength() > 0) {
                        resultMsg = msgNodes.item(0).getTextContent();
                    }
                    log.error("공공API 오류 응답: resultCode={}, resultMsg={}", resultCode, resultMsg);
                    throw new RuntimeException("공공API 오류: " + resultCode + " - " + resultMsg);
                }
            }

            // item 요소 파싱
            NodeList itemNodes = doc.getElementsByTagName("item");
            for (int i = 0; i < itemNodes.getLength(); i++) {
                Node node = itemNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    TradeItem item = parseItem(element);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("XML 파싱 오류: {}", e.getMessage(), e);
            throw new RuntimeException("XML 파싱에 실패했습니다: " + e.getMessage(), e);
        }

        return items;
    }

    /**
     * 개별 item 요소를 TradeItem으로 변환한다.
     */
    private static TradeItem parseItem(Element element) {
        try {
            TradeItem item = new TradeItem();

            item.setAptNm(getTagValue(element, "aptNm"));
            item.setDealAmount(getTagValue(element, "dealAmount"));
            item.setDealYear(getTagValue(element, "dealYear"));
            item.setDealMonth(getTagValue(element, "dealMonth"));
            item.setDealDay(getTagValue(element, "dealDay"));
            item.setUmdNm(getTagValue(element, "umdNm"));
            item.setJibun(getTagValue(element, "jibun"));
            item.setRoadNm(getTagValue(element, "roadNm"));
            item.setSggCd(getTagValue(element, "sggCd"));
            item.setUmdCd(getTagValue(element, "umdCd"));
            item.setCdealType(getTagValue(element, "cdealType"));
            item.setDealingGbn(getTagValue(element, "dealingGbn"));

            // 숫자 필드 파싱
            String excluUseArStr = getTagValue(element, "excluUseAr");
            if (excluUseArStr != null && !excluUseArStr.isEmpty()) {
                try {
                    item.setExcluUseAr(Double.parseDouble(excluUseArStr.trim()));
                } catch (NumberFormatException e) {
                    item.setExcluUseAr(0);
                }
            }

            String floorStr = getTagValue(element, "floor");
            if (floorStr != null && !floorStr.isEmpty()) {
                try {
                    item.setFloor(Integer.parseInt(floorStr.trim()));
                } catch (NumberFormatException e) {
                    item.setFloor(0);
                }
            }

            String buildYearStr = getTagValue(element, "buildYear");
            if (buildYearStr != null && !buildYearStr.isEmpty()) {
                try {
                    item.setBuildYear(Integer.parseInt(buildYearStr.trim()));
                } catch (NumberFormatException e) {
                    item.setBuildYear(0);
                }
            }

            // dealAmount 공백 제거
            if (item.getDealAmount() != null) {
                item.setDealAmount(item.getDealAmount().trim());
            }

            // cdealType이 null이면 빈 문자열로
            if (item.getCdealType() == null) {
                item.setCdealType("");
            } else {
                item.setCdealType(item.getCdealType().trim());
            }

            // dealingGbn이 null이면 빈 문자열로
            if (item.getDealingGbn() == null) {
                item.setDealingGbn("");
            } else {
                item.setDealingGbn(item.getDealingGbn().trim());
            }

            // aptNm이 없으면 스킵
            if (item.getAptNm() == null || item.getAptNm().trim().isEmpty()) {
                return null;
            }

            return item;

        } catch (Exception e) {
            log.warn("item 파싱 오류: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Element에서 특정 태그의 텍스트 값을 추출한다.
     *
     * @param element 부모 요소
     * @param tagName 태그 이름
     * @return 텍스트 값 또는 null
     */
    private static String getTagValue(Element element, String tagName) {
        NodeList nodeList = element.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            Node node = nodeList.item(0);
            if (node.getTextContent() != null) {
                return node.getTextContent();
            }
        }
        return null;
    }
}
