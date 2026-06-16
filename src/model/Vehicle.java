package src.model;

import src.graph.CostMetric;

/*
 Contains vehicle performance models.
 
 Computes:
 - speed
 - travel time
 - fuel usage
 
 based on:
 - slope
 - distance
 - surface type
*/

public class Vehicle {
    // base values -> can be modified later
    public static final double BASE_SPEED = 25.0;        // in m/s
    public static final double BASE_FUELRATE = 0.500;    // in mL/s

    public static void calculate(Edge edge) {
        double distance = edge.getMetric(
                   CostMetric.DISTANCE);
        double grade = edge.getMetric(
                   CostMetric.GRADE);

        //------------------------------------- 
        // // Rolling Resistance 
        //------------------------------------- 

        double rollingResistance;

        switch (edge.surfaceType.toLowerCase()) {
            case "paved": rollingResistance = 2.0; break;
            case "gravel": rollingResistance = 4.0; break;
            case "dirt":
            case "soil": rollingResistance = 6.0; break;
            case "sand": rollingResistance = 12.0; break;
            default: rollingResistance = 5.0;
        }

        // positive -> uphill, negative -> downhill
        double totalResistance = rollingResistance + grade;

        //----------------------------------------------------
        // SPEED MODEL
        //----------------------------------------------------
        double speed;

        if (totalResistance <= 0) {// downhill
            speed = BASE_SPEED * 
                    (1.0 + 0.15 * Math.tanh(-totalResistance / 8.0));
        } else { // uphill
            speed = BASE_SPEED *
                    Math.exp(-totalResistance / 18.0);
        }

        // realistic haul truck bounds
        speed = Math.max(8.0, Math.min(speed, 70.0));

        //----------------------------------------------------
        // FUEL MODEL
        //----------------------------------------------------
        double fuelRate;

        if (grade >= 0) {
            fuelRate =
                    BASE_FUELRATE *
                    (1.0 +
                    0.03 * totalResistance +
                    0.0015 * totalResistance * totalResistance);

        } else {
            fuelRate =
                    BASE_FUELRATE *
                    (0.65 +
                    0.01 * rollingResistance);
        }

        //----------------------------------------------------
        // TIME
        //----------------------------------------------------
        double travelTime = distance / speed;
        edge.setMetric(CostMetric.TIME, travelTime);

        //----------------------------------------------------
        // FUEL
        //----------------------------------------------------
        double fuelUsed = fuelRate * travelTime;
        edge.setMetric(CostMetric.FUEL, fuelUsed);
    }
}