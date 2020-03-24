import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public abstract class BaseXMLParser {

    protected final String PROJECT_ID = getProjectID();
    protected final String BASE_DIR = String.format("%1$s_files", PROJECT_ID);
    protected final String COVERAGE_REPORT_DIR = String.format("%1$s_coverage_reports", PROJECT_ID);
    protected final String COVERAGE_REPORT_PATH = String.format("%1$s/%2$s", BASE_DIR, COVERAGE_REPORT_DIR);
    protected final String ALL_TESTS_FILE = String.format("%1$s/%2$s_all_tests", BASE_DIR, PROJECT_ID);

    private static final DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    protected static DocumentBuilder docBuilder;

    private List<String> coverageReportPaths;

    protected BaseXMLParser() {
        try {
            docBuilder = dbFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            System.out.println("Error initialization DocumentBuilder");
        }
        coverageReportPaths = fileWalk(COVERAGE_REPORT_PATH);
    }

    protected abstract String getProjectID();

    private List<String> fileWalk(String walkDir) {
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

    /**
     * Constructs the file where each row number corresponds to some test id and at each row corresponding
     * to its id test name.
     *
     * @param filePathList - list of paths to whole reports
     */
    private void saveIdNameMapping(List<String> filePathList) {
        // parse math_all_tests_1 file again.
        final List<String> fullTestsNames = new ArrayList<>();
        boolean isNeedToRewriteAllTestFile = false;
        try (BufferedReader br = new BufferedReader(new FileReader(ALL_TESTS_FILE))) {
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
            for (int i = 0; i < filePathList.size(); i++) {
                String[] rawFilePathParts = filePathList.get(i).split("/");
                String rawFileName = rawFilePathParts[rawFilePathParts.length - 1].replace(".xml", "");
                String methodName = "";
                try {
                    methodName = rawFileName.split("___")[1];
                } catch (ArrayIndexOutOfBoundsException e) {
                    e.printStackTrace();
                    System.out.println(rawFileName);
                }
                String className = rawFileName.split("___")[0];
                String partialTestName = String.format("%1$s::%2$s", className, methodName);

                for (String fullTestsName : fullTestsNames) {
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

    private void rewriteAllTestsToCorrectFormat(List<String> fullTestsNames) {
        // rewrite ALL_TESTS_FILE to the correct format.
        try (PrintStream output = new PrintStream(new File(ALL_TESTS_FILE))) {
            for (int i = 0; i < fullTestsNames.size(); i++) {
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

    private void saveCoverageToFile(int[][] allTestsElementCoverage, String lvlOfCoverage) {
        File fileToSaveMatrix = new File(
                String.format("%1$s/%2$s_%3$s_coverage_matrix.txt", BASE_DIR, PROJECT_ID, lvlOfCoverage)
        );
        try (PrintStream output = new PrintStream(fileToSaveMatrix)) {
            for (int i = 0; i < allTestsElementCoverage[0].length; i++) {  // rows iteration;
                int rowCumulativeCoverage = 0;
                boolean lastRow = false;
                if (i == allTestsElementCoverage[0].length - 1) {
                    lastRow = true;
                }
                StringBuilder outString = new StringBuilder();
                for (int j = 0; j < allTestsElementCoverage.length; j++) {  // columns iteration;
                    outString.append(String.format("%1$s ", allTestsElementCoverage[j][i]));
                    if (allTestsElementCoverage[j][i] == 1 && !lastRow) {
                        rowCumulativeCoverage++;
                    }
                }
                outString.append(rowCumulativeCoverage);
                output.println(outString);
            }
            /*for (int[] singleTestCoverage : allTestsElementCoverage) {
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


    /**
     * Obtain searched elements from one of the reports.
     *
     * @return - count of founded el-s.
     */
    protected abstract int getElementsCount(String coverageReportPath);


    /**
     * parse single Cobertura XML-report
     *
     * @return single row of coverage matrix (which elements cover by the test's report).
     */
    protected abstract int[] parseCoverageXMLReport(String coverageReportPath, int totalElementsCount);


    /**
     * generate and save coverage matrix from the parsed reports.
     *
     * @param lvlOfCoverage - e.g. "line" or "method" (better to create an Enum for that in future). Only uses in
     *                      result filename.
     */
    protected void generateCoverageMatrix(String lvlOfCoverage) {
        int testsCount = coverageReportPaths.size();  // count of founded reports.

        // count of all the elements (e.g. class, methods, statements) inside the program (its provided report).
        // coverageReportPaths.get(0) - take random report for that, since all the reports have the same structure
        // (same amount of parsed blocks, different just coverage (hits)).
        int totalElementsCount = getElementsCount(coverageReportPaths.get(0));

        /*
         * coverage matrix at the method lvl, each of the row in the matrix - single array with conditions,
         * which are covered by single program test.
         */
        int[][] allTestsElementsCoverage = new int[testsCount][totalElementsCount];

        for (int i = 0; i < testsCount; i++) {
            String coverageReportPath = coverageReportPaths.get(i);
            int[] singleTestElementCoverage = parseCoverageXMLReport(coverageReportPath, totalElementsCount);
            allTestsElementsCoverage[i] = singleTestElementCoverage;
        }
        saveCoverageToFile(allTestsElementsCoverage, lvlOfCoverage);
    }

}
