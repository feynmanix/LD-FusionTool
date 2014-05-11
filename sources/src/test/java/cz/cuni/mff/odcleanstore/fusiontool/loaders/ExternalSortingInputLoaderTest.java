package cz.cuni.mff.odcleanstore.fusiontool.loaders;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import cz.cuni.mff.odcleanstore.conflictresolution.ResolvedStatement;
import cz.cuni.mff.odcleanstore.conflictresolution.URIMapping;
import cz.cuni.mff.odcleanstore.conflictresolution.impl.ResolvedStatementImpl;
import cz.cuni.mff.odcleanstore.conflictresolution.impl.util.SpogComparator;
import cz.cuni.mff.odcleanstore.fusiontool.config.*;
import cz.cuni.mff.odcleanstore.fusiontool.exceptions.ODCSFusionToolException;
import cz.cuni.mff.odcleanstore.fusiontool.io.EnumSerializationFormat;
import cz.cuni.mff.odcleanstore.fusiontool.urimapping.URIMappingIterable;
import cz.cuni.mff.odcleanstore.fusiontool.urimapping.URIMappingIterableImpl;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFWriter;
import org.openrdf.rio.Rio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static cz.cuni.mff.odcleanstore.fusiontool.testutil.ContextAwareStatementIsEqual.contextAwareStatementIsEqual;
import static cz.cuni.mff.odcleanstore.fusiontool.testutil.ODCSFTTestUtils.createHttpStatement;
import static cz.cuni.mff.odcleanstore.fusiontool.testutil.ODCSFTTestUtils.createHttpUri;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;

public class ExternalSortingInputLoaderTest {
    private static final ValueFactoryImpl VALUE_FACTORY = ValueFactoryImpl.getInstance();
    public static final SpogComparator SPOG_COMPARATOR = new SpogComparator();

    @Rule
    public TemporaryFolder testDir = new TemporaryFolder();

    private AtomicInteger testFileCounter = new AtomicInteger(0);

    private URIMappingIterable uriMapping;

    /** A general case of test data */
    private Collection<Statement> testInput1 = ImmutableList.of(
            // triples that map to the same statement
            createHttpStatement("sa", "pa", "oa", "g1"),
            createHttpStatement("sb", "pb", "ob", "g1"),

            // additional triple for the mapped resource
            createHttpStatement("sa", "p1", "oa", "ga"),

            // two identical triples
            createHttpStatement("s1", "p1", "o1", "g1"),
            createHttpStatement("s1", "p1", "o1", "g1"),

            // a conflict cluster with three values
            createHttpStatement("s2", "p2", "o1", "g1"),
            createHttpStatement("s2", "p2", "o2", "g2"),
            createHttpStatement("s2", "p2", "o3", "g3"),

            // resource description with two conflict clusters
            createHttpStatement("s3", "p1", "o1", "g4"),
            createHttpStatement("s3", "p2", "o2", "g5"),
            createHttpStatement("s3", "p2", "o3", "g5"),

            // resource description with mapped property and object and no named graph
            createHttpStatement("s4", "pa", "o1"),
            createHttpStatement("s4", "p1", "oa")
    );

    private Collection<Statement> testInput2 = ImmutableList.of(
            createHttpStatement("s1", "p1", "o1", "g1"),
            createHttpStatement("x", "y", "z", "g1")
    );

    /** Map of subject -> predicate -> set of statements in conflict cluster for {@link #testInput1} */
    private Map<Resource, Map<URI, Set<Statement>>> conflictClustersMap1;


    @Before
    public void setUp() throws Exception {
        // init URI mapping
        // map sa, sb -> sx; pa, pb -> px; oa, ob -> ox; ga, gb -> gx
        URIMappingIterableImpl uriMappingImpl = new URIMappingIterableImpl(ImmutableSet.of(
                createHttpUri("sx").toString(),
                createHttpUri("px").toString(),
                createHttpUri("ox").toString(),
                createHttpUri("gx").toString()));
        uriMappingImpl.addLink(createHttpUri("sa").toString(), createHttpUri("sx").toString());
        uriMappingImpl.addLink(createHttpUri("sb").toString(), createHttpUri("sx").toString());
        uriMappingImpl.addLink(createHttpUri("pa").toString(), createHttpUri("px").toString());
        uriMappingImpl.addLink(createHttpUri("pb").toString(), createHttpUri("px").toString());
        uriMappingImpl.addLink(createHttpUri("oa").toString(), createHttpUri("ox").toString());
        uriMappingImpl.addLink(createHttpUri("ob").toString(), createHttpUri("ox").toString());
        uriMappingImpl.addLink(createHttpUri("ga").toString(), createHttpUri("gx").toString());
        uriMappingImpl.addLink(createHttpUri("gb").toString(), createHttpUri("gx").toString());
        this.uriMapping = uriMappingImpl;

        // init map of conflict clusters
        Collection<Statement> mappedTestInput1 = applyMapping(testInput1, uriMapping);
        conflictClustersMap1 = createConflictClusterMap(mappedTestInput1);

    }

    @Test
    public void iteratesOverAllStatementsWithAppliedUriMapping() throws Exception {
        // Act
        SortedSet<Statement> result = new TreeSet<Statement>(SPOG_COMPARATOR);
        ExternalSortingInputLoader inputLoader = new ExternalSortingInputLoader(
                createFileAllTriplesLoader(testInput1),
                testDir.getRoot(),
                ConfigConstants.DEFAULT_FILE_PARSER_CONFIG,
                Long.MAX_VALUE, false);
        try {
            inputLoader.initialize(uriMapping);
            while (inputLoader.hasNext()) {
                result.addAll(inputLoader.nextQuads());
            }
        } finally {
            inputLoader.close();
        }

        // Assert
        SortedSet<Statement> expectedStatementsSet = new TreeSet<Statement>(SPOG_COMPARATOR);
        expectedStatementsSet.addAll(applyMapping(testInput1, uriMapping));
        assertThat(result.size(), equalTo(expectedStatementsSet.size()));

        Statement[] expectedStatements = expectedStatementsSet.toArray(new Statement[0]);
        Statement[] actualStatements = result.toArray(new Statement[0]);
        for (int i = 0; i < expectedStatements.length; i++) {
            // compare including named graphs
            assertThat(actualStatements[i], contextAwareStatementIsEqual(expectedStatements[i]));
        }
    }

    @Test
    public void nextQuadsReturnsCompleteConflictClusters() throws Exception {
        // Act
        List<Collection<Statement>> statementBlocks = new ArrayList<Collection<Statement>>();
        ExternalSortingInputLoader inputLoader = null;
        try {
            inputLoader = new ExternalSortingInputLoader(createFileAllTriplesLoader(testInput1), testDir.getRoot(),
                    ConfigConstants.DEFAULT_FILE_PARSER_CONFIG, Long.MAX_VALUE, false);
            inputLoader.initialize(uriMapping);
            while (inputLoader.hasNext()) {
                statementBlocks.add(inputLoader.nextQuads());
            }
        } finally {
            inputLoader.close();
        }

        // Assert
        for (Collection<Statement> block : statementBlocks) {
            // for each conflict cluster in a block returned by nextQuads(),
            // all quads belonging to this conflict cluster must be present.
            for (Statement statement : block) {
                Set<Statement> conflictCluster = conflictClustersMap1.get(statement.getSubject()).get(statement.getPredicate());
                boolean blockContainsEntireConflictCluster = block.containsAll(conflictCluster);
                if (!blockContainsEntireConflictCluster) {
                    fail(String.format("Block %s doesn't contain the entire conflict cluster %s", block, conflictCluster));
                }
            }
        }
    }

    @Test
    public void nextQuadsReturnsDescriptionOfSingleResource() throws Exception {
        // Act
        List<Collection<Statement>> statementBlocks = new ArrayList<Collection<Statement>>();
        ExternalSortingInputLoader inputLoader = null;
        try {
            inputLoader = new ExternalSortingInputLoader(createFileAllTriplesLoader(testInput1), testDir.getRoot(),
                    ConfigConstants.DEFAULT_FILE_PARSER_CONFIG, Long.MAX_VALUE, false);
            inputLoader.initialize(uriMapping);
            while (inputLoader.hasNext()) {
                statementBlocks.add(inputLoader.nextQuads());
            }
        } finally {
            inputLoader.close();
        }

        // Assert
        for (Collection<Statement> block : statementBlocks) {
            assertFalse(block.isEmpty());
            Resource firstSubject = block.iterator().next().getSubject();
            for (Statement statement : block) {
                assertThat(statement.getSubject(), equalTo(firstSubject));
            }
        }
    }

    @Test
    public void resourceIsDescribedInSingleNextQuadsResult() throws Exception {
        // Act
        List<Collection<Statement>> statementBlocks = new ArrayList<Collection<Statement>>();
        ExternalSortingInputLoader inputLoader = new ExternalSortingInputLoader(createFileAllTriplesLoader(testInput1), testDir.getRoot(),
                ConfigConstants.DEFAULT_FILE_PARSER_CONFIG, Long.MAX_VALUE, false);
        try {
            inputLoader.initialize(uriMapping);
            while (inputLoader.hasNext()) {
                statementBlocks.add(inputLoader.nextQuads());
            }
        } finally {
            inputLoader.close();
        }

        // Assert
        Set<Resource> subjectsFromPreviousBlocks = new HashSet<Resource>();
        for (Collection<Statement> block : statementBlocks) {
            Set<Resource> subjectsFromCurrentBlock = new HashSet<Resource>();
            for (Statement statement : block) {
                assertFalse(subjectsFromPreviousBlocks.contains(statement.getSubject()));
                subjectsFromCurrentBlock.add(statement.getSubject());
            }
            subjectsFromPreviousBlocks.addAll(subjectsFromCurrentBlock);
        }
    }

    @Test
    public void worksWithLowMemory() throws Exception {
        // Act
        final long maxMemory = 1;
        SortedSet<Statement> result = new TreeSet<Statement>(SPOG_COMPARATOR);
        ExternalSortingInputLoader inputLoader = new ExternalSortingInputLoader(createFileAllTriplesLoader(testInput1), testDir.getRoot(),
                ConfigConstants.DEFAULT_FILE_PARSER_CONFIG, maxMemory, false);
        try {
            inputLoader.initialize(uriMapping);
            while (inputLoader.hasNext()) {
                result.addAll(inputLoader.nextQuads());
            }
        } finally {
            inputLoader.close();
        }

        // Assert
        SortedSet<Statement> expectedStatementsSet = new TreeSet<Statement>(SPOG_COMPARATOR);
        expectedStatementsSet.addAll(applyMapping(testInput1, uriMapping));
        assertThat(result.size(), equalTo(expectedStatementsSet.size()));
    }

    @Test
    public void worksOnEmptyStatements() throws Exception {
        // Act & assert
        ExternalSortingInputLoader inputLoader = new ExternalSortingInputLoader(
                createFileAllTriplesLoader(Collections.<Statement>emptySet()),
                testDir.getRoot(),
                ConfigConstants.DEFAULT_FILE_PARSER_CONFIG,
                Long.MAX_VALUE, false);
        try {
            inputLoader.initialize(uriMapping);

            assertFalse(inputLoader.hasNext());
        } finally {
            inputLoader.close();
        }
    }

    @Test
    public void clearsTemporaryFilesWhenClosed() throws Exception {
        // Act
        ExternalSortingInputLoader inputLoader = new ExternalSortingInputLoader(createFileAllTriplesLoader(testInput1), testDir.getRoot(),
                ConfigConstants.DEFAULT_FILE_PARSER_CONFIG, Long.MAX_VALUE, false);
        try {
            inputLoader.initialize(uriMapping);
            if (inputLoader.hasNext()) {
                // call only once
                inputLoader.nextQuads();
            }
        } finally {
            inputLoader.close();
        }

        // Assert
        File[] filesInWorkingDir = testDir.getRoot().listFiles();
        assertThat(filesInWorkingDir.length, equalTo(1)); // only the input file should remain
    }

    @Test
    public void clearsTemporaryFilesOnError() throws Exception {
        // Act
        ExternalSortingInputLoader inputLoader = null;
        Exception caughtException = null;
        try {
            DataSourceConfig dataSource = createFileDataSource(testInput1);
            File inputFile = new File(dataSource.getParams().get(ConfigParameters.DATA_SOURCE_FILE_PATH));
            FileWriter inputFileWriter = new FileWriter(inputFile, true);
            inputFileWriter.append("xyz;");
            inputFileWriter.close();

            Set<AllTriplesLoader> dataSources = Collections.singleton(
                    (AllTriplesLoader) new AllTriplesFileLoader(dataSource, ConfigConstants.DEFAULT_FILE_PARSER_CONFIG));
            inputLoader = new ExternalSortingInputLoader(dataSources, testDir.getRoot(),
                    ConfigConstants.DEFAULT_FILE_PARSER_CONFIG, Long.MAX_VALUE, false);
            inputLoader.initialize(uriMapping);
            if (inputLoader.hasNext()) {
                // call only once
                inputLoader.nextQuads();
            }
        } catch (Exception e) {
            caughtException = e;
        } finally {
            inputLoader.close();
        }

        // Assert
        assertThat(caughtException, instanceOf(ODCSFusionToolException.class));
        File[] filesInWorkingDir = testDir.getRoot().listFiles();
        assertThat(filesInWorkingDir.length, equalTo(1)); // only the input file should remain
    }

    @Test
    public void updateWithResolvedStatementsDoesNotThrowException() throws Exception {
        // Act
        ExternalSortingInputLoader inputLoader = new ExternalSortingInputLoader(createFileAllTriplesLoader(testInput1), testDir.getRoot(),
                ConfigConstants.DEFAULT_FILE_PARSER_CONFIG, Long.MAX_VALUE, false);
        try {
            inputLoader.initialize(uriMapping);
            while (inputLoader.hasNext()) {
                Collection<Statement> statements = inputLoader.nextQuads();
                Statement firstStatement = statements.iterator().next();
                ResolvedStatement resolvedStatement = new ResolvedStatementImpl(firstStatement, 0.5, Collections.singleton(firstStatement.getContext()));
                inputLoader.updateWithResolvedStatements(Collections.singleton(resolvedStatement));
            }
        } finally {
            inputLoader.close();
        }
    }

    @Test
    public void readsMultipleInputFiles() throws Exception {
        // Act
        SortedSet<Statement> result = new TreeSet<Statement>(SPOG_COMPARATOR);
        ExternalSortingInputLoader inputLoader = new ExternalSortingInputLoader(
                createFileAllTriplesLoader(testInput1, testInput2),
                testDir.getRoot(),
                ConfigConstants.DEFAULT_FILE_PARSER_CONFIG,
                Long.MAX_VALUE, false);
        try {
            inputLoader.initialize(uriMapping);
            while (inputLoader.hasNext()) {
                result.addAll(inputLoader.nextQuads());
            }
        } finally {
            inputLoader.close();
        }

        // Assert
        SortedSet<Statement> expectedStatementsSet = new TreeSet<Statement>(SPOG_COMPARATOR);
        expectedStatementsSet.addAll(applyMapping(testInput1, uriMapping));
        expectedStatementsSet.addAll(applyMapping(testInput2, uriMapping));
        assertThat(result.size(), equalTo(expectedStatementsSet.size()));

        Statement[] expectedStatements = expectedStatementsSet.toArray(new Statement[0]);
        Statement[] actualStatements = result.toArray(new Statement[0]);
        for (int i = 0; i < expectedStatements.length; i++) {
            // compare including named graphs
            assertThat(actualStatements[i], contextAwareStatementIsEqual(expectedStatements[i]));
        }
    }

    @Test
    public void filtersUnmappedSubjectsWhenOutputMappedSubjectsOnlyIsTrue() throws Exception {
        // Arrange
        ArrayList<Statement> statements = new ArrayList<Statement>();
        statements.add(createHttpStatement("s1", "p1", "o1", "g1"));
        statements.add(createHttpStatement("s3", "p1", "o1", "g1"));
        statements.add(createHttpStatement("s2", "p1", "o1", "g1"));
        statements.add(createHttpStatement("s4", "p1", "o1", "g1"));

        URIMappingIterableImpl uriMapping = new URIMappingIterableImpl(ImmutableSet.of(
                createHttpUri("sx").toString(), createHttpUri("s2").toString()));
        uriMapping.addLink(createHttpUri("s1").toString(), createHttpUri("sx").toString());
        uriMapping.addLink(createHttpUri("s2").toString(), createHttpUri("sy").toString());

        // Act
        SortedSet<Statement> result = new TreeSet<Statement>(SPOG_COMPARATOR);
        ExternalSortingInputLoader inputLoader = null;
        try {
            inputLoader = new ExternalSortingInputLoader(createFileAllTriplesLoader(statements), testDir.getRoot(),
                    ConfigConstants.DEFAULT_FILE_PARSER_CONFIG, Long.MAX_VALUE, true);
            inputLoader.initialize(uriMapping);
            while (inputLoader.hasNext()) {
                result.addAll(inputLoader.nextQuads());
            }
        } finally {
            inputLoader.close();
        }

        // Assert
        SortedSet<Statement> expectedStatementsSet = new TreeSet<Statement>(SPOG_COMPARATOR);
        expectedStatementsSet.add(createHttpStatement("sx", "p1", "o1", "g1"));
        expectedStatementsSet.add(createHttpStatement("s2", "p1", "o1", "g1"));

        assertThat(result.size(), equalTo(expectedStatementsSet.size()));
        Statement[] expectedStatements = expectedStatementsSet.toArray(new Statement[0]);
        Statement[] actualStatements = result.toArray(new Statement[0]);
        for (int i = 0; i < expectedStatements.length; i++) {
            assertThat(actualStatements[i], contextAwareStatementIsEqual(expectedStatements[i]));
        }
    }

    private Collection<AllTriplesLoader> createFileAllTriplesLoader(Collection<Statement>... sourceStatements) throws IOException, RDFHandlerException {
        if (sourceStatements.length == 1) {
            DataSourceConfig dataSourceConfig = createFileDataSource(sourceStatements[0]);
            return Collections.singleton((AllTriplesLoader) new AllTriplesFileLoader(dataSourceConfig, ConfigConstants.DEFAULT_FILE_PARSER_CONFIG));
        }
        ArrayList<AllTriplesLoader> result = new ArrayList<AllTriplesLoader>();
        for (Collection<Statement> statements : sourceStatements) {
            DataSourceConfig dataSourceConfig = createFileDataSource(statements);
            result.add(new AllTriplesFileLoader(dataSourceConfig, ConfigConstants.DEFAULT_FILE_PARSER_CONFIG));
        }
        return result;
    }

    private DataSourceConfig createFileDataSource(Collection<Statement> statements) throws IOException, RDFHandlerException {
        DataSourceConfigImpl result = new DataSourceConfigImpl(
                EnumDataSourceType.FILE,
                "test-input-file" + testFileCounter.getAndIncrement() + ".trig");
        EnumSerializationFormat format = EnumSerializationFormat.TRIG;
        File inputFile = createInputFile(statements, format.toSesameFormat());
        result.getParams().put(ConfigParameters.DATA_SOURCE_FILE_PATH, inputFile.getAbsolutePath());
        result.getParams().put(ConfigParameters.DATA_SOURCE_FILE_FORMAT, format.name());
        return result;
    }

    private File createInputFile(Collection<Statement> statements, RDFFormat format) throws IOException, RDFHandlerException {
        File inputFile = testDir.newFile();
        FileOutputStream outputStream = new FileOutputStream(inputFile);
        RDFWriter rdfWriter = Rio.createWriter(format, outputStream);
        rdfWriter.startRDF();
        rdfWriter.handleComment("Test input file");
        for (Statement statement : statements) {
            rdfWriter.handleStatement(statement);
        }
        rdfWriter.endRDF();
        outputStream.close();
        return inputFile;
    }

    private static Map<Resource, Map<URI, Set<Statement>>> createConflictClusterMap(Collection<Statement> statements) {
        Map<Resource, Map<URI, Set<Statement>>> result = new HashMap<Resource, Map<URI, Set<Statement>>>();
        for (Statement st : statements) {
            Map<URI, Set<Statement>> subjectRecord = result.get(st.getSubject());
            if (subjectRecord == null) {
                subjectRecord = new HashMap<URI, Set<Statement>>();
                result.put(st.getSubject(), subjectRecord);
            }

            Set<Statement> predicateRecord = subjectRecord.get(st.getPredicate());
            if (predicateRecord == null) {
                predicateRecord = new HashSet<Statement>();
                subjectRecord.put(st.getPredicate(), predicateRecord);
            }

            predicateRecord.add(st);
        }
        return result;
    }

    private static Collection<Statement> applyMapping(Collection<Statement> statements, URIMapping uriMapping) {
        ImmutableList.Builder<Statement> result = ImmutableList.builder();
        for (Statement statement : statements) {
            result.add(VALUE_FACTORY.createStatement(
                    (Resource) mapNode(statement.getSubject(), uriMapping),
                    (URI) mapNode(statement.getPredicate(), uriMapping),
                    mapNode(statement.getObject(), uriMapping),
                    statement.getContext()
            ));
        }
        return result.build();
    }

    private static Value mapNode(Value value, URIMapping uriMapping) {
        if (value instanceof URI) {
            return uriMapping.mapURI((URI) value);
        }
        return value;
    }
}