import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import java.io.File;

public class XMLStatementCoverageParser extends BaseXMLParser {

    private static final String PROJECT_ID = "lang";
    private static final String LVL_OF_COVERAGE = "line";
    private static final String PARSED_ELEMENT_TAG_NAME = "line";

    public static void main(String[] args) {
        new XMLStatementCoverageParser().generateCoverageMatrix(LVL_OF_COVERAGE);
    }

    @Override
    protected String getProjectID() {
        return PROJECT_ID;
    }

    @Override
    protected int getElementsCount(String coverageReportPath) {
        int relevantLinesCount = 0;
        try {
            Document doc = docBuilder.parse(new File(coverageReportPath));
            NodeList nList = doc.getElementsByTagName(PARSED_ELEMENT_TAG_NAME);
            relevantLinesCount = nList.getLength();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return relevantLinesCount;
    }

    @Override
    protected int[] parseCoverageXMLReport(String coverageReportPath, int totalLinesCount) {
        int[] coverageInfo = new int[totalLinesCount + 1];  // +1 for cumulative coverage column
        int cumulativeCoverage = 0;
        try {
            Document doc = docBuilder.parse(new File(coverageReportPath));
            NodeList nList = doc.getElementsByTagName(PARSED_ELEMENT_TAG_NAME);

            int lineId = 0;
            for (int temp = 0; temp < nList.getLength(); temp++) {
                NamedNodeMap lineAttrs = nList.item(temp).getAttributes();

                if (Float.parseFloat(lineAttrs.getNamedItem("hits").getNodeValue()) > 0) {
                    coverageInfo[lineId] = 1;
                    cumulativeCoverage++;
                }
                lineId++;
            }
            coverageInfo[totalLinesCount] = cumulativeCoverage;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return coverageInfo;
    }
}
