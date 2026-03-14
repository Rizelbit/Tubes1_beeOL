package alternativeBot1;

import battlecode.common.*;

import java.time.Clock;
import java.util.Random;

public class RobotPlayer {
    /* ===== VARIABEL GLOBAL ===== */
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

    /* ===== MEMORY MANAGEMENT ===== */
    // Memory map
    static void updateMapMemory(RobotController rc) throws GameActionException {
        // Ambil semua tile yang terlihat dalam radius jarak pandang (4.47 petak)
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            MapLocation loc = tile.getMapLocation(); // Ambil dan simpan koordinat (x, y) nya
            // Kalau pergerakan di luar batas koordinat, lanjut ke tile berikutnya
            if (loc.x < 0 || loc.x >= 61 || loc.y < 0 || loc.y >= 61) {
                continue;
            }
            if (tile.isWall() || tile.hasRuin()) {
                mapMemory[loc.x][loc.y] = 4; // 4 menandakan wall/ruin di list
            } else if (tile.getPaint().isAlly()) {
                mapMemory[loc.x][loc.y] = 2; // 2 menandakan tile udah dicat oleh sekutu
            } else if (tile.getPaint().isEnemy()) {
                mapMemory[loc.x][loc.y] = 3; // 3 menandakan tile udah dicat oleh musuh
            } else {
                mapMemory[loc.x][loc.y] = 1; // 1 menandakan tile masih kosong
            }
        }
    }

    // Memory tower
    static void updateTowerMemory(RobotController rc) throws GameActionException {
        // Ambil semua robot sekutu dalam jarak pandang maksimal
        for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (!ally.getType().isTowerType()) { // Validasi tipe tower atau bukan
                continue; // Kalau bukan, skip
            }
            boolean known = false; // Defaultnya menara ini belum tercatat
            for (int i = 0; i < towerCount; i++) { // Cek satu per satu menara udah di list atau belum
                if (alliedTowers[i].equals(ally.getLocation())) {
                    known = true;
                    break; // Kalau ketemu, stop
                }
            }
            // Kalau ga ketemu dan list belum penuh, masukin ke list
            if (!known && towerCount < 30) {
                // Simpen lokasi menara ke indeks towerCount terus ditambah 1 (setelah dimasukin)
                alliedTowers[towerCount++] = ally.getLocation();
            }
        }
    }

    // Memory ruin
    static void updateRuinMemory(RobotController rc) throws GameActionException {
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (!tile.hasRuin()) { // Kalau ga ada ruin di tile itu, lanjut
                continue;
            }
            MapLocation loc = tile.getMapLocation();
            
            // Kalau tile dalam jarak pandang, cek ada robot apa ngga di sana
            RobotInfo robotAtRuin;
            if (rc.canSenseLocation(loc)) {
                robotAtRuin = rc.senseRobotAtLocation(loc); // Kalau ada robot, dapetin RobotInfo
            } else {
                robotAtRuin = null;
            }

            // Parameter sudah jadi tower: ada robot dan tipenya tower
            boolean alreadyTower = (robotAtRuin != null && robotAtRuin.getType().isTowerType());

            // Kalau sudah jadi tower di tile ini, hapus dari list
            if (alreadyTower) {
                for (int i = 0; i < ruinCount; i++) {
                    if (knownRuins[i] != null && knownRuins[i].equals(loc)) {
                        knownRuins[i] = knownRuins[--ruinCount];
                        knownRuins[ruinCount] = null;
                        break;
                    }
                }
            } else { // Kalau belum jadi tower, masukin ke list
                boolean known = false;
                for (int i = 0; i < ruinCount; i++) {
                    if (knownRuins[i] != null && knownRuins[i].equals(loc)) {
                        known = true;
                        break;
                    }
                }
                if (!known && ruinCount < 50) {
                    knownRuins[ruinCount++] = loc;
                }
            }
        }
    }

    /* ===== RELOAD PAINT ===== */
    // Kalau robot lagi reload, hold semua action
    static boolean tryReloadPaint(RobotController rc) throws GameActionException {
        // Batas minimal cat sebelum reload: sisa 30% dari kapasitas
        int threshold = (int)(rc.getType().paintCapacity * 0.3);
        if (rc.getPaint() >= threshold) { // Kalau masih di atas batas, lanjut aja
            return false;
        }

        MapLocation nearest = null; // Lokasi menara sekutu terdekat yang udah dicatat
        int minDist = Integer.MAX_VALUE; // Set minimal distance ke maksimal value dari integer, supaya jarak-jarak lainnya bakal selalu di bawah dia
        for (int i = 0; i < towerCount; i++) { // Tentuin tower terdekat
            int d = rc.getLocation().distanceSquaredTo(alliedTowers[i]); // Jarak robot dengan suatu tower
            if (d < minDist) {
                minDist = d;
                nearest = alliedTowers[i]; // Simpan tower terdekat
            }
        }
        // Kalau gaada sama sekali tower yang udah dicatat di sekitar
        if (nearest == null) {
            return false;
        }

        if (minDist <= 2) { // Kondisi kalau robot udah sebelahan sama menara
            if (rc.isActionReady()) { // Perhitungan reload cat
                int need = rc.getType().paintCapacity - rc.getPaint();
                if (need > 0 && rc.canTransferPaint(nearest, -need)) {
                    rc.transferPaint(nearest, -need); // Robot ambil cat dari tower (hence the -)
                }
            }
        } else if (rc.isMovementReady()) { // Kalau robot masih jauh dari tower terdekat 
            smartMoveTo(rc, nearest); // Gerak ke arah tower
        } // Di luar itu, robot diam di tempat sampe nunggu action ready di next round
        return true; // Robot lagi reload
    }
}