package se.kth.jabeja;

import org.apache.log4j.Logger;
import se.kth.jabeja.config.AnnealingPolicy;
import se.kth.jabeja.config.Config;
import se.kth.jabeja.config.NodeSelectionPolicy;
import se.kth.jabeja.io.FileIO;
import se.kth.jabeja.rand.RandNoGenerator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import static java.lang.StrictMath.pow;

public class Jabeja {

    private static final float T_MIN_STANDARD = 1;
    private static final float T_MIN_AP = 0.00001f;
    private static final Random RANDOM = new Random();

    private int counterLocal = 0;
    private int counterRandom = 0;

    // If should reset T, n of rounds to wait after T reached its minimum value
    private static final int ROUNDS_BEFORE_RESET = 400;
    // Keep track of the number of rounds that we have been waiting
    private int roundsWaitingToReset = 0;

    final static Logger logger = Logger.getLogger(Jabeja.class);
    private final Config config;
    private final HashMap<Integer/*id*/, Node/*neighbors*/> entireGraph;
    private final List<Integer> nodeIds;
    private int numberOfSwaps;
    private int round;
    private float T;
    private float T_min;
    private float alpha;
    private boolean resultFileCreated = false;

    //-------------------------------------------------------------------
    public Jabeja(HashMap<Integer, Node> graph, Config config) {
        this.entireGraph = graph;
        this.nodeIds = new ArrayList(entireGraph.keySet());
        this.round = 0;
        this.numberOfSwaps = 0;
        this.config = config;
        this.T = config.getTemperature();
        this.T_min = config.getAnnealingPolicy() == AnnealingPolicy.STANDARD ? T_MIN_STANDARD: T_MIN_AP;
        this.alpha = config.getAlpha();
    }


    //-------------------------------------------------------------------
    public void startJabeja() throws IOException {
        for (round = 0; round < config.getRounds(); round++) {
            for (int id : entireGraph.keySet()) {
                sampleAndSwap(id);
            }
            //one cycle for all nodes have completed.
            //reduce the temperature
            logger.info("Local swapped: " + this.counterLocal + ". Random swapped: " + this.counterRandom);
            this.counterLocal = 0;
            this.counterRandom = 0;
            saCoolDown();
            report();
        }
    }

    /**
     * Simulated analealing cooling function
     */
    private void saCoolDown() {
        if (T > T_min) {
            if (config.getAnnealingPolicy() == AnnealingPolicy.STANDARD) {
                T -= config.getDelta();
            } else if (config.getAnnealingPolicy() == AnnealingPolicy.EXP
                    || config.getAnnealingPolicy() == AnnealingPolicy.CUSTOM) {
                T *= config.getDelta();
            }
            logger.info("Cooling T to " + T);
        }
        if (T < T_min) {
            T = T_min;
            this.roundsWaitingToReset = 0;
        }
        if (T == T_min && this.config.shouldResetT()) {
            this.roundsWaitingToReset++;
            int roundsRemaining = ROUNDS_BEFORE_RESET - this.roundsWaitingToReset;
            logger.info("Waiting " + (roundsRemaining) + " more rounds to reset T");
            if (this.roundsWaitingToReset == ROUNDS_BEFORE_RESET) {
                this.T = this.config.getTemperature();
                this.roundsWaitingToReset = 0;
            }
        }
    }

    /**
     * Sample and swap algorithm at node p
     *
     * @param nodeId
     */
    private void sampleAndSwap(int nodeId) {
        Node partner = null;
        Node nodep = entireGraph.get(nodeId);

        if (config.getNodeSelectionPolicy() == NodeSelectionPolicy.HYBRID
                || config.getNodeSelectionPolicy() == NodeSelectionPolicy.LOCAL) {
            // swap with random neighbors
            partner = this.findPartner(nodeId, this.getNeighbors(nodep));
            if (partner != null)
                counterLocal++;
        }

        if (config.getNodeSelectionPolicy() == NodeSelectionPolicy.HYBRID
                || config.getNodeSelectionPolicy() == NodeSelectionPolicy.RANDOM) {
            // if local policy fails then randomly sample the entire graph
            if (partner == null) {
                partner = this.findPartner(nodeId, this.getSample(nodeId));
                if (partner != null)
                    counterRandom++;
            }
        }

        // swap the colors
        if (partner != null) {
            int partnerColor = partner.getColor();
            partner.setColor(nodep.getColor());
            nodep.setColor(partnerColor);
            this.numberOfSwaps++;
        }
    }

    public Node findPartner(int nodeId, Integer[] nodes) {

        Node nodep = entireGraph.get(nodeId);

        Node bestPartner = null;
        double highestBenefit = 0;

        for (Integer nodeqId : nodes) {
            Node nodeq = entireGraph.get(nodeqId);
            int dpp = this.getDegree(nodep, nodep.getColor());
            int dqq = this.getDegree(nodeq, nodeq.getColor());
            double oldScore = pow(dpp, this.alpha) + pow(dqq, this.alpha);
            int dpq = this.getDegree(nodep, nodeq.getColor());
            int dqp = this.getDegree(nodeq, nodep.getColor());
            double newScore = pow(dpq, this.alpha) + pow(dqp, this.alpha);

            if (config.getAnnealingPolicy() == AnnealingPolicy.STANDARD) {
                if (((newScore * this.T) > oldScore) && (newScore > highestBenefit)) {
                    bestPartner = nodeq;
                    highestBenefit = newScore;
                }
            } else if (config.getAnnealingPolicy() == AnnealingPolicy.EXP) {
                double ap = Math.exp((newScore - oldScore) / T);
                double ran = RANDOM.nextDouble();
                if (newScore != oldScore && ap > ran && ap > highestBenefit) {
                    bestPartner = nodeq;
                    highestBenefit = ap;
                }
            } else if (config.getAnnealingPolicy() == AnnealingPolicy.CUSTOM) {
                double ap = Math.exp((1 / oldScore - 1 / newScore) / T);
                double ran = RANDOM.nextDouble();
                if (newScore != oldScore && ap > ran && ap > highestBenefit) {
                    bestPartner = nodeq;
                    highestBenefit = ap;
                }
            }

        }

        return bestPartner;
    }

    /**
     * The the degree on the node based on color
     *
     * @param node
     * @param colorId
     * @return how many neighbors of the node have color == colorId
     */
    private int getDegree(Node node, int colorId) {
        int degree = 0;
        for (int neighborId : node.getNeighbours()) {
            Node neighbor = entireGraph.get(neighborId);
            if (neighbor.getColor() == colorId) {
                degree++;
            }
        }
        return degree;
    }

    /**
     * Returns a uniformly random sample of the graph
     *
     * @param currentNodeId
     * @return Returns a uniformly random sample of the graph
     */
    private Integer[] getSample(int currentNodeId) {
        int count = config.getUniformRandomSampleSize();
        int rndId;
        int size = entireGraph.size();
        ArrayList<Integer> rndIds = new ArrayList<Integer>();

        while (true) {
            rndId = nodeIds.get(RandNoGenerator.nextInt(size));
            if (rndId != currentNodeId && !rndIds.contains(rndId)) {
                rndIds.add(rndId);
                count--;
            }

            if (count == 0)
                break;
        }

        Integer[] ids = new Integer[rndIds.size()];
        return rndIds.toArray(ids);
    }

    /**
     * Get random neighbors. The number of random neighbors is controlled using
     * -closeByNeighbors command line argument which can be obtained from the config
     * using {@link Config#getRandomNeighborSampleSize()}
     *
     * @param node
     * @return
     */
    private Integer[] getNeighbors(Node node) {
        ArrayList<Integer> list = node.getNeighbours();
        int count = config.getRandomNeighborSampleSize();
        int rndId;
        int index;
        int size = list.size();
        ArrayList<Integer> rndIds = new ArrayList<Integer>();

        if (size <= count)
            rndIds.addAll(list);
        else {
            while (true) {
                index = RandNoGenerator.nextInt(size);
                rndId = list.get(index);
                if (!rndIds.contains(rndId)) {
                    rndIds.add(rndId);
                    count--;
                }

                if (count == 0)
                    break;
            }
        }

        Integer[] arr = new Integer[rndIds.size()];
        return rndIds.toArray(arr);
    }


    /**
     * Generate a report which is stored in a file in the output dir.
     *
     * @throws IOException
     */
    private void report() throws IOException {
        int grayLinks = 0;
        int migrations = 0; // number of nodes that have changed the initial color
        int size = entireGraph.size();

        for (int i : entireGraph.keySet()) {
            Node node = entireGraph.get(i);
            int nodeColor = node.getColor();
            ArrayList<Integer> nodeNeighbours = node.getNeighbours();

            if (nodeColor != node.getInitColor()) {
                migrations++;
            }

            if (nodeNeighbours != null) {
                for (int n : nodeNeighbours) {
                    Node p = entireGraph.get(n);
                    int pColor = p.getColor();

                    if (nodeColor != pColor)
                        grayLinks++;
                }
            }
        }

        int edgeCut = grayLinks / 2;

        logger.info("round: " + round +
                ", edge cut:" + edgeCut +
                ", swaps: " + numberOfSwaps +
                ", migrations: " + migrations);

        saveToFile(edgeCut, migrations);
    }

    private void saveToFile(int edgeCuts, int migrations) throws IOException {
        String delimiter = "\t\t";
        String outputFilePath;

        //output file name
        File inputFile = new File(config.getGraphFilePath());
        outputFilePath = config.getOutputDir() +
                File.separator +
                inputFile.getName() + "_" +
                "NS" + "_" + config.getNodeSelectionPolicy() + "_" +
                "GICP" + "_" + config.getGraphInitialColorPolicy() + "_" +
                "ANN" + "_" + config.getAnnealingPolicy() + "_" +
                "T" + "_" + config.getTemperature() + "_" +
                "RESET" + "_" + config.shouldResetT() + "_" +
                "D" + "_" + config.getDelta() + "_" +
                "RNSS" + "_" + config.getRandomNeighborSampleSize() + "_" +
                "URSS" + "_" + config.getUniformRandomSampleSize() + "_" +
                "A" + "_" + config.getAlpha() + "_" +
                "R" + "_" + config.getRounds() + ".txt";

        if (!resultFileCreated) {
            File outputDir = new File(config.getOutputDir());
            if (!outputDir.exists()) {
                if (!outputDir.mkdir()) {
                    throw new IOException("Unable to create the output directory");
                }
            }
            // create folder and result file with header
            String header = "# Migration is number of nodes that have changed color.";
            header += "\n\nRound" + delimiter + "Edge-Cut" + delimiter + "Swaps" + delimiter + "Migrations" + delimiter + "Skipped" + "\n";
            FileIO.write(header, outputFilePath);
            resultFileCreated = true;
        }

        FileIO.append(round + delimiter + (edgeCuts) + delimiter + numberOfSwaps + delimiter + migrations + "\n", outputFilePath);
    }
}
