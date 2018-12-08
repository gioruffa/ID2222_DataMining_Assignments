package se.kth.jabeja.io;

import org.apache.log4j.Logger;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import se.kth.jabeja.config.AnnealingPolicy;
import se.kth.jabeja.config.Config;
import se.kth.jabeja.config.GraphInitColorPolicy;
import se.kth.jabeja.config.NodeSelectionPolicy;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Created by salman on 10/25/16.
 */
public class CLI {
    final static Logger logger = Logger.getLogger(CLI.class);

    @Option(name = "-help", usage = "Print usages.")
    private boolean HELP = false;

    @Option(name = "-rounds", usage = "Number of rounds.")
    private int ROUNDS = 1000;

    @Option(name = "-numPartitions", usage = "Number of partitions.")
    private int NUM_PARTITIONS = 4;

    @Option(name = "-uniformRandSampleSize", usage = "Uniform random sample size.")
    private int UNIFORM_RAND_SAMPLE_SIZE = 6;

    @Option(name = "-temp", usage = "Simulated annealing temperature.")
    private float TEMPERATURE = 2;

    @Option(name = "-delta", usage = "Simulated annealing delta.")
    private float DELTA = (float) 0.003;

    @Option(name = "-seed", usage = "Seed.")
    private int SEED = 0;

    @Option(name = "-alpha", usage = "Alpah parameter")
    private float ALPHA = 2;

    @Option(name = "-randNeighborsSampleSize", usage = "Number of random neighbors sample size.")
    private int randNeighborsSampleSize = 3;

    @Option(name = "-graphInitColorSelectionPolicy", usage = "Initial color celection policy. Supported, RANDOM, ROUND_ROBIN, BATCH")
    private String GRAPH_INIT_COLOR_SELECTION_POLICY = "ROUND_ROBIN";
    private GraphInitColorPolicy graphInitColorSelectionPolicy = GraphInitColorPolicy.ROUND_ROBIN;

    @Option(name = "-nodeSelectionPolicy", usage = "Node selection plicy. Supported, RANDOM, LOCAL, HYBRID")
    private String NODE_SELECTION_POLICY = "HYBRID";
    private NodeSelectionPolicy nodeSelectionPolicy = NodeSelectionPolicy.HYBRID;

    @Option(name = "-annealingPolicy", usage = "Annealing policy. Supported, STANDARD, EXP, CUSTOM. If EXP or CUSTOM, T is set to 1")
    private String ANNEALING_POLICY = "STANDARD";
    private AnnealingPolicy annealingPolicy = AnnealingPolicy.STANDARD;

    @Option(name = "-resetT", usage = "Reset T (wait 200 rounds) when it reaches minimum value (1 for STANDARD, 0.00001 for EXP)")
    private boolean RESET_T = false;

    @Option(name = "-graph", usage = "Location of the input graph.")
    private static String GRAPH = "./graphs/ws-250.graph";

    @Option(name = "-outputDir", usage = "Location of the output file(s)")
    private static String OUTPUT_DIR = "./output";

    public Config parseArgs(String[] args) throws FileNotFoundException {
        CmdLineParser parser = new CmdLineParser(this);
        parser.setUsageWidth(80);
        try {
            // parse the arguments.
            parser.parseArgument(args);
            if (GRAPH_INIT_COLOR_SELECTION_POLICY.compareToIgnoreCase(GraphInitColorPolicy.RANDOM.toString()) == 0) {
                graphInitColorSelectionPolicy = GraphInitColorPolicy.RANDOM;
            } else if (GRAPH_INIT_COLOR_SELECTION_POLICY.compareToIgnoreCase(GraphInitColorPolicy.BATCH.toString()) == 0) {
                graphInitColorSelectionPolicy = GraphInitColorPolicy.BATCH;
            } else if (GRAPH_INIT_COLOR_SELECTION_POLICY.compareToIgnoreCase(GraphInitColorPolicy.ROUND_ROBIN.toString()) == 0) {
                graphInitColorSelectionPolicy = GraphInitColorPolicy.ROUND_ROBIN;
            } else {
                throw new IllegalArgumentException("Initial color selection policy is not supported");
            }

            if (NODE_SELECTION_POLICY.compareToIgnoreCase(NodeSelectionPolicy.RANDOM.toString()) == 0) {
                nodeSelectionPolicy = NodeSelectionPolicy.RANDOM;
            } else if (NODE_SELECTION_POLICY.compareToIgnoreCase(NodeSelectionPolicy.LOCAL.toString()) == 0) {
                nodeSelectionPolicy = NodeSelectionPolicy.LOCAL;
            } else if (NODE_SELECTION_POLICY.compareToIgnoreCase(NodeSelectionPolicy.HYBRID.toString()) == 0) {
                nodeSelectionPolicy = NodeSelectionPolicy.HYBRID;
            } else {
                throw new IllegalArgumentException("Node selection policy is not supported");
            }

            if (ANNEALING_POLICY.compareToIgnoreCase(AnnealingPolicy.STANDARD.toString()) == 0) {
                annealingPolicy = AnnealingPolicy.STANDARD;
            } else if (ANNEALING_POLICY.compareToIgnoreCase(AnnealingPolicy.EXP.toString()) == 0) {
                annealingPolicy = AnnealingPolicy.EXP;
            } else if (ANNEALING_POLICY.compareToIgnoreCase(AnnealingPolicy.CUSTOM.toString()) == 0) {
                annealingPolicy = AnnealingPolicy.CUSTOM;
            } else {
                throw new IllegalArgumentException("Annealing policy is not supported");
            }

        } catch (Exception e) {
            logger.error(e.getMessage());
            parser.printUsage(System.err);
            System.exit(-1);
        }

        File graphFile = new File(GRAPH);
        if (!graphFile.exists() || !graphFile.isFile()) {
            throw new FileNotFoundException("Graph file does not exist.");
        }

        if (HELP) {
            parser.printUsage(System.out);
            System.exit(0);
        }

        Config cfg = new Config().setRandNeighborsSampleSize(randNeighborsSampleSize)
                .setDelta(DELTA)
                .setNumPartitions(NUM_PARTITIONS)
                .setUniformRandSampleSize(UNIFORM_RAND_SAMPLE_SIZE)
                .setRounds(ROUNDS)
                .setSeed(SEED)
                .setTemperature(TEMPERATURE)
                .setGraphFilePath(GRAPH)
                .setNodeSelectionPolicy(nodeSelectionPolicy)
                .setGraphInitialColorPolicy(graphInitColorSelectionPolicy)
                .setAnnealingPolicy(annealingPolicy)
                .setResetT(RESET_T)
                .setOutputDir(OUTPUT_DIR)
                .setAlpha(ALPHA);

        if (annealingPolicy == AnnealingPolicy.EXP) {
            logger.info("Ignoring T since annealing policy is EXP");
            cfg.setTemperature(1F);
        }

        return cfg;

    }
}
