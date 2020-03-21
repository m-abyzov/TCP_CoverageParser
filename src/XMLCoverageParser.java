import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class XMLCoverageParser {

    private static final String PROJECT_ID = "math";

    public static final String BASE_DIR = String.format("%1$s_files", PROJECT_ID);
    private static final String COVERAGE_REPORT_DIR = String.format("%1$s_coverage_reports", PROJECT_ID);
    public static final String COVERAGE_REPORT_PATH = String.format("%1$s/%2$s", BASE_DIR, COVERAGE_REPORT_DIR);
    public static final String ALL_TESTS_FILE = String.format("%1$s/%2$s_all_tests", BASE_DIR, PROJECT_ID);

    private static final List<String> INIT_METHODS = Arrays.asList("<clinit>", "<init>");

    private static final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    private static DocumentBuilder docBuilder;

    public XMLCoverageParser() {
        try {
            docBuilder = dbFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            System.out.println("Error initialization DocumentBuilder");
        }
    }

    private static void rewriteAllTestsToCorrectFormat(List<String> fullTestsNames) {
        // rewrite ALL_TESTS_FILE to the correct format.
        try (PrintStream output = new PrintStream(new File(ALL_TESTS_FILE))) {
            for (int i=0; i<fullTestsNames.size(); i++) {
                if (i == fullTestsNames.size() - 1) {
                    output.print(fullTestsNames.get(i));
                } else {
                    output.println(fullTestsNames.get(i));
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Constructs the file where each row number corresponds to some test id and at each row corresponding
     * to its id test name.
     * @param filePathList - list of paths to whole reports
     */
    private static void saveIdNameMapping(List<String> filePathList) {
        // parse math_all_tests_1 file again.
        final List<String> fullTestsNames = new ArrayList<>();
        boolean isNeedToRewriteAllTestFile = false;
        try(BufferedReader br = new BufferedReader(new FileReader(ALL_TESTS_FILE))) {
            String rawLine = br.readLine();
            while (rawLine != null) {
                if (!rawLine.isEmpty()) {
                    if (rawLine.split("\\(").length > 1) {
                        // if came here -> just raw all test file was put, need to rewrite it
                        isNeedToRewriteAllTestFile = true;
                        String methodName = rawLine.split("\\(")[0];
                        String className = rawLine.split("\\(")[1];
                        className = className.substring(0, className.length() - 1);
                        fullTestsNames.add(String.format("%1$s::%2$s", className, methodName));
                    } else if (rawLine.split("::").length > 1) {
                        fullTestsNames.add(rawLine);
                    }
                }
                rawLine = br.readLine();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        if (isNeedToRewriteAllTestFile) {
            rewriteAllTestsToCorrectFormat(fullTestsNames);
        }

        try (PrintStream output = new PrintStream(new File(String.format("%1$s/%2$s_id_name_mapping.txt", BASE_DIR, PROJECT_ID)))) {
            for (int i=0; i<filePathList.size(); i++) {
                String[] rawFilePathParts = filePathList.get(i).split("/");
                String rawFileName = rawFilePathParts[rawFilePathParts.length - 1].replace(".xml", "");
                String methodName = rawFileName.split("___")[1];
                String className = rawFileName.split("___")[0];
                String partialTestName = String.format("%1$s::%2$s", className, methodName);

                for (String fullTestsName: fullTestsNames) {
                    if (fullTestsName.contains(partialTestName)) {
                        output.println(fullTestsName);
                        break;
                    }
                }
//                output.println(String.format("%1$s,%2$s", i, rawFileName));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static List<String> fileWalk(String walkDir) {
        List<String> filePathList = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Paths.get(walkDir))) {
            paths
                    .filter(Files::isRegularFile)
                    .forEach(path -> filePathList.add(path.toString()));
        } catch (IOException ex) {
            ex.printStackTrace();
            System.out.println("Exception during the file-walking.");
        }
        saveIdNameMapping(filePathList);  // row corresponds to the concrete test_id;
        return filePathList;
    }

    private Map<Integer, String> createIndexForTests(String coverageReportPath) {
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
    }

    /**
     * parse the provided Cobertura XML-report
     * @param coverageReportPath - path to a report.
     * @param methodsCount - actual method counts, except constructors.
     * @return array where each index corresponds to
     */
    private int[] parseCoverageXMLReport(String coverageReportPath, int methodsCount) {
        int[] coverageInfo = new int[methodsCount + 1];  // +1 for cumulative coverage column
        int cumulativeCoverage = 0;
        try {
            Document doc = docBuilder.parse(new File(coverageReportPath));
            NodeList nList = doc.getElementsByTagName("method");

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
            coverageInfo[methodsCount] = cumulativeCoverage;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return coverageInfo;
    }

    // TODO: create mapping btw test name and its index (row in the constructed coverage matrix).
    public static void main(String[] args) {
        List<String> coverageReportPaths = fileWalk(COVERAGE_REPORT_PATH);
        int testsCount = coverageReportPaths.size();  // count of founded reports.

        // TODO: UNCOMMENT FOR THE USAGE!
//        XMLCoverageParser parser = new XMLCoverageParser();
//
//        Map<Integer, String> testsIndex = parser.createIndexForTests(coverageReportPaths.get(0));
//        int methodsCount = testsIndex.size();  // count of all the methods inside the program (its provided report).
//
//        /*
//         * coverage matrix at the method lvl, each of the row in the matrix - single array with conditions,
//         * which are covered by single program test.
//         */
//        int[][] allTestsMethodCoverage = new int[testsCount][methodsCount];
//
//        for (int i=0; i<testsCount; i++) {
//            String coverageReportPath = coverageReportPaths.get(i);
//            int[] singleTestMethodCoverage = parser.parseCoverageXMLReport(coverageReportPath, methodsCount);
//            allTestsMethodCoverage[i] = singleTestMethodCoverage;
//        }
//        saveCoverageToFile(allTestsMethodCoverage);
    }

    private static void saveCoverageToFile(int[][] allTestsMethodCoverage ) {
        try (PrintStream output = new PrintStream(new File(String.format("%1$s/%2$s_coverage_matrix.txt", BASE_DIR, PROJECT_ID)))) {
            for (int i=0; i<allTestsMethodCoverage[0].length; i++) {  // rows iteration;
                int rowCumulativeCoverage = 0;
                boolean lastRow = false;
                if (i == allTestsMethodCoverage[0].length - 1) {
                    lastRow = true;
                }
                StringBuilder outString = new StringBuilder();
                for (int j=0; j<allTestsMethodCoverage.length; j++) {  // columns iteration;
                    outString.append(String.format("%1$s ", allTestsMethodCoverage[j][i]));
                    if (allTestsMethodCoverage[j][i] == 1 && !lastRow) {
                        rowCumulativeCoverage++;
                    }
                }
                outString.append(rowCumulativeCoverage);
                output.println(outString);
            }

            /*for (int[] singleTestCoverage : allTestsMethodCoverage) {
                StringBuilder outString = new StringBuilder();
                for (int coveragePoint : singleTestCoverage) {
                    outString.append(String.format("%1$s ", coveragePoint));
                }
                output.println(outString);
//                output.println(String.format(" %1$s", coveragePoint));
            }*/
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
