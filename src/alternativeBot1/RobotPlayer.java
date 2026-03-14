package alternativeBot1;

import battlecode.common.*;
import java.util.Random;

public class RobotPlayer {
    // Variabel global
    // Hitung sudah berapa ronde robot ini hidup
    static int turnCount = 0;
    
    // Random number generator buat probability spawn tower dan variasi pergerakan robot (supaya ga ke arah situ-situ aja)
    static final Random randomNumberGenerator = new Random(1234);

    // Array berisi 8 arah gerak yang memungkinkan
    static final Direction[] directions = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST,
    };

    // Array 2D buat robot nyimpen history tile map yang udah dilewatin
    // Keterangan nilai elemen: 0 = unknown, 1 = kosong, 2 = udah dicat sekutu, 3 = udah dicat musuh, 4 = tembok/ruin
    static int[][] mapMemory = new int[61][61];

    // List yang isinya 40 tiles terakhir yang dikunjungi robot
    static MapLocation[] visitedTiles = new MapLocation[40];

    // Menunjuk indeks di list yang akan ditimpa (karena maksimal 40, pasti bakal ngulang dari awal kalau udah penuh)
    static int visitedIndex = 0;

    // Daftar koordinat tower sekutu yang pernah dilihat robot
    static MapLocation[] alliedTowers = new MapLocation[30];

    // Berapa tower yang udah tercatat
    static int towerCount = 0;

    // Daftar koordinat ruins yang pernah dilihat robot dan belum jadi tower
    static MapLocation[] knownRuins = new MapLocation[50];

    // Berapa ruin yang udah tercatat
    static int ruinCount = 0;

    // Fungsi run
    public static void run(RobotController rc) throws GameActionException {
        while (true) { // --> Selama robot masih hidup
            turnCount = turnCount + 1;
            try {
                updateMapMemory(rc); // Update ingatan tentang kondisi peta berdasarkan apa yang terlihat sekarang
                updateTowerMemory(rc); // Update daftar tower sekutu yang diketahui
                updateRuinMemory(rc); // Update daftar ruin yang belum jadi menara

                switch (rc.getType()) { // Jalanin fungsi yang sesuai tipe robot
                    case SOLDIER: runSoldier(rc); break;
                    case SPLASHER: runSplasher(rc); break;
                    case MOPPER: runMopper(rc); break;
                    default: runTower(rc); break; // Langsung semua tipe tower
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                Clock.yield(); // Tanda selesai giliran robot ini
            }
        }
    }

}