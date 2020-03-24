import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class XMLMethodCoverageXMLParser extends BaseXMLParser {

    private static final String PROJECT_ID = "lang";
    private static final String LVL_OF_COVERAGE = "method";
    private static final String PARSED_ELEMENT_TAG_NAME = "method";

    private static final List<String> INIT_METHODS = Arrays.asList("<clinit>", "<init>");

    /**
     *  In case if it is required to know, which testId corresponds to which name
     * @param coverageReportPath
     * @return
     */
    /*private Map<Integer, String> createIndexForTests(String coverageReportPath) {
        final Map<Integer, String> methodIdsToNames = new LinkedHashMap<>();  // save the original xml-report order.
        int methodId = 0;
        try {
            Document doc = docBuilder.parse(new File(coverageReportPath));
            NodeList nList = doc.getElementsByTagName("method");

            for (int temp = 0; temp < nList.getLength(); temp++) {
                NamedNodeMap methodAttrs = nList.item(temp).getAttributes();

                String methodName = methodAttrs.getNamedItem("name").getNodeValue();
                if (! INIT_METHODS.contains(methodName)) {
                    methodIdsToNames.put(methodId, methodName);
                    methodId ++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return methodIdsToNames;
    }*/

    public static void main(String[] args) {
        new XMLMethodCoverageXMLParser().generateCoverageMatrix(LVL_OF_COVERAGE);
    }

    @Override
    protected String getProjectID() {
        return PROJECT_ID;
    }

    @Override
    protected int getElementsCount(String coverageReportPath) {
        int relevantMethodsCount = 0;
        try {
            Document doc = docBuilder.parse(new File(coverageReportPath));
            NodeList nList = doc.getElementsByTagName(PARSED_ELEMENT_TAG_NAME);

            for (int temp = 0; temp < nList.getLength(); temp++) {
                NamedNodeMap methodAttrs = nList.item(temp).getAttributes();

                String methodName = methodAttrs.getNamedItem("name").getNodeValue();
                if (!INIT_METHODS.contains(methodName)) {
                    relevantMethodsCount++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return relevantMethodsCount;
    }

    /**
     * parse the provided Cobertura XML-report
     *
     * @param coverageReportPath - path to a report.
     * @param totalMethodsCount  - actual method counts, except constructors.
     * @return array where each index corresponds to
     */
    @Override
    protected int[] parseCoverageXMLReport(String coverageReportPath, int totalMethodsCount) {
        int[] coverageInfo = new int[totalMethodsCount + 1];  // +1 for cumulative coverage column
        int cumulativeCoverage = 0;
        try {
            Document doc = docBuilder.parse(new File(coverageReportPath));
            NodeList nList = doc.getElementsByTagName(PARSED_ELEMENT_TAG_NAME);

            int methodId = 0;
            for (int temp = 0; temp < nList.getLength(); temp++) {
                NamedNodeMap methodAttrs = nList.item(temp).getAttributes();

                String methodName = methodAttrs.getNamedItem("name").getNodeValue();
                if (INIT_METHODS.contains(methodName)) {
                    continue;
                }

                if (Float.parseFloat(methodAttrs.getNamedItem("line-rate").getNodeValue()) > 0) {
                    coverageInfo[methodId] = 1;
                    cumulativeCoverage++;
                }
                methodId++;
            }
            coverageInfo[totalMethodsCount] = cumulativeCoverage;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return coverageInfo;
    }
}
