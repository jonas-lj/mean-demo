package dk.alexandra.meandemo;

import dk.alexandra.fresco.framework.Application;
import dk.alexandra.fresco.framework.DRes;
import dk.alexandra.fresco.framework.Party;
import dk.alexandra.fresco.framework.builder.numeric.ProtocolBuilderNumeric;
import dk.alexandra.fresco.framework.builder.numeric.field.MersennePrimeFieldDefinition;
import dk.alexandra.fresco.framework.configuration.NetworkConfiguration;
import dk.alexandra.fresco.framework.configuration.NetworkConfigurationImpl;
import dk.alexandra.fresco.framework.network.Network;
import dk.alexandra.fresco.framework.network.socket.SocketNetwork;
import dk.alexandra.fresco.framework.sce.SecureComputationEngine;
import dk.alexandra.fresco.framework.sce.SecureComputationEngineImpl;
import dk.alexandra.fresco.framework.sce.evaluator.BatchedProtocolEvaluator;
import dk.alexandra.fresco.framework.sce.evaluator.EvaluationStrategy;
import dk.alexandra.fresco.framework.util.AesCtrDrbg;
import dk.alexandra.fresco.lib.fixed.FixedNumeric;
import dk.alexandra.fresco.lib.fixed.SFixed;
import dk.alexandra.fresco.stat.Statistics;
import dk.alexandra.fresco.suite.spdz.SpdzProtocolSuite;
import dk.alexandra.fresco.suite.spdz.SpdzResourcePool;
import dk.alexandra.fresco.suite.spdz.SpdzResourcePoolImpl;
import dk.alexandra.fresco.suite.spdz.storage.SpdzDataSupplier;
import dk.alexandra.fresco.suite.spdz.storage.SpdzDummyDataSupplier;
import dk.alexandra.fresco.suite.spdz.storage.SpdzOpenedValueStoreImpl;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ComputeMean {

  public static void main(String[] arguments) {
    if (arguments.length != 2) {
      throw new IllegalArgumentException("Usage: java -jar compute-mean.jar [myId] [myInput]");
    }

    // Configure fresco
    final int myId = Integer.parseInt(arguments[0]);
    final int noParties = 3;
    final int modBitLength = 256;
    final int maxBitLength = 180;
    final int maxBatchSize = 4096;
    final double myInput = Double.parseDouble(arguments[1]);

    Party me = new Party(myId, "localhost", 9000 + myId);
    List<Party> others = getOthers(myId);

    Map<Integer, Party> parties = new HashMap<>();
    parties.put(myId, me);
    parties.put(others.get(0).getPartyId(), others.get(0));
    parties.put(others.get(1).getPartyId(), others.get(1));

    NetworkConfiguration networkConfiguration = new NetworkConfigurationImpl(myId,
        parties);
    Network network = new SocketNetwork(networkConfiguration);
    MersennePrimeFieldDefinition definition = MersennePrimeFieldDefinition.find(modBitLength);
    SpdzProtocolSuite suite = new SpdzProtocolSuite(maxBitLength);

    // Use "dummy" multiplication triples to simulate doing only the online phase
    SpdzDataSupplier supplier = new SpdzDummyDataSupplier(myId, noParties, definition,
        BigInteger.valueOf(1234));

    SpdzResourcePool resourcePool = new SpdzResourcePoolImpl(myId, noParties,
        new SpdzOpenedValueStoreImpl(), supplier,
        AesCtrDrbg::new);

    BatchedProtocolEvaluator<SpdzResourcePool> evaluator =
        new BatchedProtocolEvaluator<>(EvaluationStrategy.SEQUENTIAL_BATCHED.getStrategy(), suite,
            maxBatchSize);
    SecureComputationEngine<SpdzResourcePool, ProtocolBuilderNumeric> sce = new SecureComputationEngineImpl<>(
        suite, evaluator);

    Instant start = Instant.now();

    BigDecimal out = sce
        .runApplication(new MeanDemo(myId, myInput),
            resourcePool, network, Duration.ofMinutes(30));

    System.out.println(out);
    System.out.println("Took " + Duration.between(start, Instant.now()));
  }

  /** Build list of the other parties */
  private static List<Party> getOthers(int myId) {
    List<Party> others = new ArrayList<>(3);
    for (int i = 1; i <= 3; i++) {
      if (i != myId) {
        others.add(new Party(i, "localhost", 9000 + i));
      }
    }
    return others;
  }

  /** The actual computation taking inputs and computing the mean */
  public static class MeanDemo implements
      Application<BigDecimal, ProtocolBuilderNumeric> {

    private final int myId;
    private final double input;

    public MeanDemo(int myId, double input) {
      this.myId = myId;
      this.input = input;
    }

    @Override
    public DRes<BigDecimal> buildComputation(ProtocolBuilderNumeric builder) {
      return builder.par(par -> {
        // Input values to the computation in the same order
        List<DRes<SFixed>> inputs = new ArrayList<>();
        for (int id = 1; id <= 3; id++) {
          if (id == myId) {
            inputs.add(FixedNumeric.using(par).input(input, myId));
          } else {
            inputs.add(FixedNumeric.using(par).input(null, id));
          }
        }
        return DRes.of(inputs);
      }).seq((seq, inputs) -> {
        // Compute the sample mean of the secret shared values and reveal
        // the result to all parties.
        DRes<SFixed> result = Statistics.using(seq).sampleMean(inputs);
        return FixedNumeric.using(seq).open(result);
      });
    }
  }

}
