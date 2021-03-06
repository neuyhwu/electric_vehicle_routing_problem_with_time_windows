package at.ac.tuwien.otl.ss18.pwlk.distance;

import at.ac.tuwien.otl.ss18.pwlk.constraints.ConstraintsChecker;
import at.ac.tuwien.otl.ss18.pwlk.util.Pair;
import at.ac.tuwien.otl.ss18.pwlk.valueobjects.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DistanceHolder {


  private double[][] interNodeDistancesArray;
  private List[][] chargingStationsByNodesArray;

  private final Map<Customer, Double> customerDepotDistances = new HashMap<>();
  private final Map<Customer, List<Pair<AbstractNode, Double>>> interCustomerDistances =
          new HashMap<>();
  private final Map<Customer, List<Pair<AbstractNode, Double>>> customerChargingStationDistances =
          new HashMap<>();

  private final ProblemInstance problemInstance;

  public DistanceHolder(final ProblemInstance problemInstance) {
    this.problemInstance = problemInstance;
    init();
  }

  private void init() {
    calculateCustomerToCustomerDistances();
    calculateCustomerToDepotDistances();
    calculateCustomerToRechargingDistances();

    calculateInterNodeDistances();
  }

  public double getInterNodeDistance(AbstractNode from, AbstractNode to) {
    return interNodeDistancesArray[from.getIndex()][to.getIndex()];
  }

  public double getMaxDistanceToDepot(){
    Depot depot = problemInstance.getDepot();

    double maxDistance = 0;

    for(int i = 0; i < interNodeDistancesArray[depot.getIndex()].length; i++) {
      double curr_distance = interNodeDistancesArray[depot.getIndex()][i];

      if (curr_distance > maxDistance) {
        maxDistance = curr_distance;
      }
    }

    return maxDistance;
  }

  // calculate node to node distances
  private void calculateInterNodeDistances() {
    List<AbstractNode> list = Stream.concat(
            problemInstance.getCustomers().stream(),
            problemInstance.getChargingStations().stream()
    ).collect(Collectors.toList());
    list.add(problemInstance.getDepot());

    interNodeDistancesArray = new double[list.size()][list.size()];
    chargingStationsByNodesArray = new List[list.size()][list.size()];

    for (final AbstractNode fromNode : list) {
      for (final AbstractNode toNode : list) {
        final double distance =
                DistanceCalculator.calculateDistanceBetweenNodes(fromNode, toNode);
        interNodeDistancesArray[fromNode.getIndex()][toNode.getIndex()] = distance;
        calculateChargingStationsByNode(fromNode, toNode);
      }
    }
  }

  private void calculateChargingStationsByNode(AbstractNode from, AbstractNode to) {
    List<Pair<AbstractNode, Double>>  list =
            calculateNearestRechargingStationsForCustomerInDistance(from, to);
    chargingStationsByNodesArray[from.getIndex()][to.getIndex()] = list;
  }

  public List<Pair<AbstractNode, Double>> getNearestRechargingStationsForCustomerInDistance(AbstractNode from, AbstractNode to) {
    return chargingStationsByNodesArray[from.getIndex()][to.getIndex()];
  }


  private void calculateCustomerToDepotDistances() {
    final AbstractNode depot = problemInstance.getDepot();
    for (final Customer c : problemInstance.getCustomers()) {
      final double distance = DistanceCalculator.calculateDistanceBetweenNodes(depot, c);
      customerDepotDistances.put(c, distance);
    }
  }

  private void calculateCustomerToCustomerDistances() {
    for (final Customer initialPoint : problemInstance.getCustomers()) {
      final List<Pair<AbstractNode, Double>> distances = new LinkedList<>();
      for (final Customer to : problemInstance.getCustomers()) {
        final ConstraintsChecker constraintsChecker = new ConstraintsChecker(problemInstance, initialPoint, to);
        if (!constraintsChecker.violatesPreprocessingConstraints()) {
          final double distance =
                  DistanceCalculator.calculateDistanceBetweenNodes(initialPoint, to);
          distances.add(new Pair<>(to, distance));
        }
      }
      interCustomerDistances.put(initialPoint, distances);
    }
  }

  private void calculateCustomerToRechargingDistances() {
    for (final Customer initialPoint : problemInstance.getCustomers()) {
      final List<Pair<AbstractNode, Double>> distances = new LinkedList<>();
      for (final ChargingStations to : problemInstance.getChargingStations()) {
        final double distance = DistanceCalculator.calculateDistanceBetweenNodes(initialPoint, to);
        distances.add(new Pair<>(to, distance));
      }
      customerChargingStationDistances.put(initialPoint, distances);
    }
  }

  public double getDistanceToDepotForCustomer(final Customer customer) {
    return customerDepotDistances.get(customer);
  }

  public double calculateDistanceBetweenCustomers(final Customer from, final Customer to) {
    return interCustomerDistances
            .get(from)
            .stream()
            .filter(
                    pair -> pair.getKey().getId().equalsIgnoreCase(to.getId())) // only one pair will remain
            .mapToDouble(Pair::getValue) // mapping to the distance
            .sum(); // using sum here because it returns a double and does not affect the distance
  }

  public List<Pair<AbstractNode, Double>> getNearestCustomersForCustomer(final AbstractNode from) {
    final OptionalDouble minDistance = calculateMinDistanceInList(interCustomerDistances.get(from));
    return calculateMinNodeBasedOnDistance(interCustomerDistances.get(from), minDistance);
  }

  public List<Pair<AbstractNode, Double>> getNearestRechargingStationsForCustomer(
          final Customer from) {
    final OptionalDouble minDistance =
            calculateMinDistanceInList(customerChargingStationDistances.get(from));
    return calculateMinNodeBasedOnDistance(customerChargingStationDistances.get(from), minDistance);
  }

  // add at first charging stations from the target, then charging stations from the departure node
  private List<Pair<AbstractNode, Double>> calculateNearestRechargingStationsForCustomerInDistance(final AbstractNode targetNode, final AbstractNode maxDistanceNode) {
    List<ChargingStations> chargingStationList = problemInstance.getChargingStations();
    double maxDistance =  DistanceCalculator.calculateDistanceBetweenNodes(targetNode, maxDistanceNode);

    List<Pair<AbstractNode, Double>> stations = new ArrayList<>();
    for(ChargingStations chargingStations : chargingStationList) {
      double distance = DistanceCalculator.calculateDistanceBetweenNodes(targetNode, chargingStations);

      if (distance < maxDistance) {
        stations.add(new Pair(chargingStations, distance));
      }
    }

    for(ChargingStations chargingStations : chargingStationList) {
      double distance = DistanceCalculator.calculateDistanceBetweenNodes(maxDistanceNode, chargingStations);
      double actualDistance = DistanceCalculator.calculateDistanceBetweenNodes(targetNode, chargingStations);

      if (distance < maxDistance) {
        stations.add(new Pair(chargingStations, actualDistance));
      }
    }

    Collections.sort(stations, Comparator.comparing(Pair::getValue));

    return stations;
  }

  private OptionalDouble calculateMinDistanceInList(
          final List<Pair<AbstractNode, Double>> distances) {
    return distances.stream().mapToDouble(Pair::getValue).min();
  }

  private List<Pair<AbstractNode, Double>> calculateMinNodeBasedOnDistance(
          final List<Pair<AbstractNode, Double>> distances, final OptionalDouble minDistance) {
    if (minDistance.isPresent()) {
      return distances
              .stream()
              .filter(p -> p.getValue() == minDistance.getAsDouble())
              .collect(Collectors.toList());
    } else {
      return Collections.emptyList();
    }
  }

  public Map<Customer, List<Pair<AbstractNode, Double>>> getInterCustomerDistances() {
    return interCustomerDistances;
  }
}
