package org.swinburne.engine.SearchSetting;

import org.swinburne.engine.FScoreTreeComparator;
import org.swinburne.engine.HeuristicSetting.StraightLineDistanceTimeHeuristic;
import org.swinburne.model.Graph;
import org.swinburne.model.Node;
import org.swinburne.model.NodeType;
import org.swinburne.model.Tree.TreeNode;
import org.swinburne.model.Way;
import org.swinburne.util.UnitConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.PriorityQueue;

public class BestTimeSearch extends SearchSetting {

    public BestTimeSearch() {
        super("Best Time Search", new String[]{"besttime", "time", "speed"});
        heuristic = new StraightLineDistanceTimeHeuristic();
    }

    @Override
    public void computeDirection(Graph graph, Node start, Node destination) {
        resetSearch();

        this.startNode = start;
        this.destinationNode = destination;

        sldStartToFinish = UnitConverter.geopositionDistance(start, destination);
        startTime = System.nanoTime();

        try {
            path = new ArrayList<>();
            PriorityQueue<TreeNode<Node>> frontiers = new PriorityQueue<>(50, new FScoreTreeComparator());

            TreeNode<Node> rootNode = new TreeNode<>(start);

            ArrayList<Node> visited = new ArrayList<>();

            rootNode.getObject().setGCost(0);
            rootNode.getObject().setFValue(heuristic.calculateHeuristic(graph, start, start, destination));

            frontiers.add(rootNode);

            TreeNode<Node> selectedTreeNode;
            while ((selectedTreeNode = frontiers.poll()) != null) {
                Node selectedNode = selectedTreeNode.getObject();

                if (selectedNode == destination) {
                    deriveSolution(selectedTreeNode);
                    return;
                }
                visitedCount++;
                visited.add(selectedNode);

                for (Way w : selectedNode.getWayArrayList()) {

                    Node[] adjacentNodes = w.getAdjacents(selectedNode);
                    if (adjacentNodes == null) {
                        continue;
                    }
                    for (Node n : adjacentNodes) {
                        if (visited.contains(n)) continue;

                        double distanceTravelled = UnitConverter.geopositionDistance(selectedNode.getLatitude(), selectedNode.getLongitude(), n.getLatitude(), n.getLongitude());
                        double timeTraversed =  distanceTravelled / UnitConverter.kmhToMs(w.getSpeedLimitKmh());

                        if (n.getType() == NodeType.Intersection) timeTraversed += AVERAGE_INTERSECTION_TIME;
                        if (selectedTreeNode.getWay() != null && selectedTreeNode.getWay() != w) timeTraversed+= SearchSetting.AVERAGE_TURNING_TIME;
                        double totalGScore = selectedNode.getGCost() + timeTraversed;

                        boolean contained = true;
                        if (!frontiers.contains(n)) contained = false;
                        else if (totalGScore >= n.getGCost()) continue;

                        TreeNode<Node> newTreeNode = new TreeNode<>(n);

                        newTreeNode.setWay(w);
                        selectedTreeNode.addChild(newTreeNode);
                        newTreeNode.setTime(selectedTreeNode.getTime() + timeTraversed);
                        newTreeNode.setDistance(selectedTreeNode.getDistance() + distanceTravelled);

                        double signal = 0;
                        if (selectedTreeNode.getMetaData().get("traffic-signal") != null)
                            signal = selectedTreeNode.getMetaData().get("traffic-signal");

                        if (n.getType() == NodeType.Intersection) signal += 1;

                        newTreeNode.getMetaData().put("traffic-signal", signal);

                        n.setGCost(totalGScore);
                        n.setFValue(n.getGCost() + heuristic.calculateHeuristic(graph, n, start, destination));

                        if (!contained) {
                            frontiers.add(newTreeNode);
                            frontierCount++;
                        }

                        drawFrontier(selectedNode, n);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected ArrayList<Node> deriveSolution(TreeNode<Node> destination) {
        long endTime = System.nanoTime();
        processTimeMS = (endTime - startTime) / 1000000;

        ArrayList<Node> result = new ArrayList<>();
        TreeNode<Node> selectedTreeNode = destination;
        timeTaken = destination.getTime();
        totalDistance = destination.getDistance();
        if (destination.getMetaData().get("traffic-signal") != null)
            trafficSignalPassed = (int) (double) destination.getMetaData().get("traffic-signal");
        else trafficSignalPassed = 0;
        while (selectedTreeNode != null) {
            result.add(selectedTreeNode.getObject());
            if (selectedTreeNode.getParent() != null) {
                totalDistance += UnitConverter.geopositionDistance(selectedTreeNode.getObject(), selectedTreeNode.getParent().getObject());
            }
            selectedTreeNode = selectedTreeNode.getParent();
        }
        Collections.reverse(result);
        path = result;
        solutionFound = true;

        return result;
    }
}
