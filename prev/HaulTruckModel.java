package prev;
import java.util.Scanner;

public class HaulTruckModel {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.print("Distance (km): ");
        double distance = sc.nextDouble();

        System.out.print("Slope (% grade): ");
        double slope = sc.nextDouble();

        System.out.print("Surface (paved, gravel, dirt, sand): ");
        String surface = sc.next();

        // Example Caterpillar 793 values
        double baseSpeed = 60.0;      // km/h
        double baseFuelRate = 150.0;  // L/hr

        double speedMultiplier;
        double fuelMultiplier;

        switch (surface.toLowerCase()) {
            case "gravel":
                speedMultiplier = 0.8;
                fuelMultiplier = 1.1;
                break;
            case "dirt":
                speedMultiplier = 0.7;
                fuelMultiplier = 1.2;
                break;
            case "sand":
                speedMultiplier = 0.5;
                fuelMultiplier = 1.5;
                break;
            default: // paved
                speedMultiplier = 1.0;
                fuelMultiplier = 1.0;
        }

        // simple slope model
        double slopeSpeedFactor = Math.max(0.3, 1.0 - slope * 0.03);
        double slopeFuelFactor = 1.0 + slope * 0.08;

        double effectiveSpeed =
                baseSpeed *
                        speedMultiplier *
                        slopeSpeedFactor;

        double fuelRate =
                baseFuelRate *
                        fuelMultiplier *
                        slopeFuelFactor;

        double travelTimeHours =
                distance / effectiveSpeed;

        double fuelUsed =
                fuelRate * travelTimeHours;

        System.out.println("\n--- Results ---");
        System.out.printf("Effective Speed: %.2f km/h%n", effectiveSpeed);
        System.out.printf("Travel Time: %.2f hours%n", travelTimeHours);
        System.out.printf("Fuel Used: %.2f liters%n", fuelUsed);

        sc.close();
    }
}
