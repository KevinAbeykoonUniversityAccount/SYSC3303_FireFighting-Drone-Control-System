public class Launcher {
    public static void main(String[] args) {

        new Thread(() -> {
            try {
                FireIncidentMain.main(new String[]{"fire_events.csv"});
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(() -> {
            try {
                DroneMain.main(new String[]{"1", "2", "3"});
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(() -> {
            try {
                SchedulerMain.main(new String[]{"60"});
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}