import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class RollerCoaster {

    private final static int NUMBEROFPEOPLE = 100; // Total number of people in park
    private final static int NUMBEROFTRAINS = 10; // Total number of trains in park
    private final static int SEATSINTRAIN = 12; // Seats in every train
    private final static int MAXTIMETOBEREADY = 15 * 1000; // Max time for a person to be in queue for a train
    private static final int RIDETIME = 5 * 1000; // Duration of ride
    private static final int RESTARTTIME = 2 * 1000; // Duration for a train to start over
    private static final int TOTALTIME = 5 * 60 * 1000; // Duration of day

    private static Random rand = new Random();

    private static int peopleInPark = 0;
    private static int trainsInPark = 0;

    private static ArrayList<Semaphore> peopleWaiting = new ArrayList<>();
    private static ArrayList<Semaphore> trainReadyToMove = new ArrayList<>();
    private static ArrayList<ConcurrentHashMap<Integer, Semaphore>> peopleOnTheMove = new ArrayList<>();
    private static int firstTrainInLine = 0;

    private static synchronized int nextTrainOf(int train) {
        return (train+1) % NUMBEROFTRAINS;
    }


    public static class Person extends Thread {

        private int id;

        Person() {

            synchronized (this) {
                id = peopleInPark;
                peopleInPark++;
            }

            System.out.println("Person with id " + id + " entered the park.");

            start();
        }

        @Override
        public void run() {

            try {

                // If no trains are in park, wait till there are.
                synchronized (this) {
                    while (trainsInPark == 0)
                        wait(1000);
                }

                while (true) {


                    // Wait till the person wants to enter a train
                    Thread.sleep(rand.nextInt(MAXTIMETOBEREADY));

                    System.out.println("Person with id " + id + " wants to enter the train...");

                    // Wait in line till a seat is empty and take it.
                    while (true) {
                        if (peopleWaiting.get(firstTrainInLine).tryAcquire())
                            break;
                    }
                    int ourTrain = firstTrainInLine;
                    peopleOnTheMove.get(ourTrain).put(id, new Semaphore(0));

                    // Train full
                    if (peopleWaiting.get(ourTrain).availablePermits() == 0)
                        trainReadyToMove.get(ourTrain).release();

                    // Wait till end of the ride
                    peopleOnTheMove.get(ourTrain).get(id).acquire();
                    peopleOnTheMove.get(ourTrain).remove(id);


                }
            } catch (InterruptedException e) {
                System.out.println("Person with id " + id + " is leaving.");
            }
        }
    }

    private static class Train extends Thread {

        private int id;

        Train() {

            synchronized (this) {
                id = trainsInPark;
                trainsInPark++;
                trainReadyToMove.add(new Semaphore(0));
                peopleWaiting.add(new Semaphore(SEATSINTRAIN));
                peopleOnTheMove.add(new ConcurrentHashMap<>());
            }

            System.out.println("Train with id " + id + " entered the park.");

            start();
        }

        @Override
        public void run() {

            try {
                while (true) {


                    System.out.println("Train " + id + " on the line.");

                    // Full Train
                    trainReadyToMove.get(id).acquire();

                    System.out.println("Train " + id + " ready to go.");

                    // Wait till the train is first at the line
                    while (id != firstTrainInLine)
                        wait();

                    // The first train is on the move, change first train and notify the change
                    synchronized (this) {
                        firstTrainInLine = nextTrainOf(firstTrainInLine);
                        notifyAll();
                    }

                    // Ride time
                    Thread.sleep(RIDETIME);

                    // Release everybody who was in the train
                    for (int p : peopleOnTheMove.get(id).keySet()) {
                        peopleOnTheMove.get(id).get(p).release();
                    }

                    // Wait till the train is ready again for ride.
                    Thread.sleep(RESTARTTIME);
                    peopleWaiting.get(id).release(SEATSINTRAIN);


                }
            } catch (InterruptedException e) {
                System.out.println("Train with id " + id + " stops rides.");
            }
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("Theme park is now open!!!");
            System.out.println();
            System.out.println();

            ArrayList<Thread> totalPeople = new ArrayList<>();
            ArrayList<Thread> totalTrains = new ArrayList<>();

            for (int i=0; i<NUMBEROFPEOPLE; i++)
                totalPeople.add(new Person());

            for (int i=0; i<NUMBEROFTRAINS; i++)
                totalTrains.add(new Train());

            Thread.sleep(TOTALTIME);

            // Terminates all threads
            for (Thread running : totalPeople) {
                running.interrupt();
                running.join();
            }

            for (Thread running : totalTrains) {
                running.interrupt();
                running.join();
            }

            System.out.println();
            System.out.println("The theme park is closing...");


        } catch (InterruptedException e) {
            System.out.println("Disaster at the park!!!");
        }
    }

}
