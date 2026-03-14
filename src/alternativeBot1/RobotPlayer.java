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
            int distance = rc.getLocation().distanceSquaredTo(alliedTowers[i]); // Jarak robot dengan suatu tower
            if (distance < minDist) {
                minDist = distance;
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

    /* ===== TOWER LOGIC ===== */
    static void runTower(RobotController rc) throws GameActionException {
        // Prioritas 1: spawn robot (utamanya soldier)
        // Pakai buffer cat = 300 dan chips = 500 supaya masih ada cukup setidaknya satu kali lagi ngespawn
        if (rc.isActionReady() && rc.getPaint() >= 300 && rc.getChips() >= 500) {
            int randomRoll = randomNumberGenerator.nextInt(10);
            UnitType unit;
            if (randomRoll < 4) { // Probabilitas spawn soldier = 40%
                unit = UnitType.SOLDIER;
            } else if (randomRoll < 7) { // Probabilitas spawn splasher = 30%
                unit = UnitType.SPLASHER;
            } else { // Probabilitas spawn mopper = 30%
                unit = UnitType.MOPPER;
            }

            // Kalau ga cukup chipsnya, fallback ke soldier yang paling murah
            if (unit == UnitType.SPLASHER && rc.getChips() < 400) {
                unit = UnitType.SOLDIER;
            }
            if (unit == UnitType.MOPPER && rc.getChips() < 300) {
                unit = UnitType.SOLDIER;
            }

            // Coba spawn ke semua arah sampai ketemu yang valid (ga tabrakan sama robot lain misal)
            for (Direction dir : directions) {
                MapLocation loc = rc.getLocation().add(dir);
                if (rc.canBuildRobot(unit, loc)) {
                    rc.buildRobot(unit, loc);
                    break;
                }
            }
        }

        // Prioritas 2: attack musuh
        for (RobotInfo enemy : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) { // Scan semua robot musuh yang berada dalam jangkauan
            if (rc.canAttack(enemy.getLocation())) {
                rc.attack(enemy.getLocation()); // Serang yang pertama bisa diserang
                break;
            }
        }

        // Prioritas 3: upgrade tower
        if (rc.canUpgradeTower(rc.getLocation())) { // Cek lokasi tower saat ini, masih bisa upgrade apa ngga (chips cukup dan belum level maksimal)
            rc.upgradeTower(rc.getLocation());
        }
    }

    /* ===== SOLDIER LOGIC ===== */
    static void runSoldier(RobotController rc) throws GameActionException {
        // Prioritas 1: reload cat kalau hampir habis
        if (tryReloadPaint(rc)) {
            return;
        }

        // Prioritas 2: Cek ruin yang langsung terlihat (dalam jarak pandang)
        MapLocation myLocation = rc.getLocation();
        MapInfo targetRuin = null; // Ruin yang bakal dikerjain nanti di round ini
        for (MapInfo title : rc.senseNearbyMapInfos()) { // Scan semua tile dalam jangkauan
            if (!tile.hasRuin()) { // Cari yang punya ruin
                continue;
            }
            MapLocation location = tile.getMapLocation;
            if (!rc.canSenseLocation(location)) {
                continue;
            }
            RobotInfo robotAtLocation = rc.senseRobotAtLocation(location);
            if (robotAtLocation == null) { // Kalau tidak ada robot di ruin, pasti belum ada menara
                targetRuin = tile;
                break;
            }
        }

        if (targetRuin != null) {
            buildTowerAtRuin(rc, targetRuin.getMapLocation());
            return;
        }

        // Prioritas 3: cari ruin di memory yang paling jauh dari menara sekutu
        MapLocation bestRuin = null; // Ruin terbaik buat disamperin nanti
        int maxDistanceFromTowers = -1;

        // Untuk setiap ruin di memory, hitung seberapa jauh ruin itu dari tower sekutu terdekat
        // Pilih ruin yang jaraknya paling jauh
        for (int i = 0; i < ruinCount; i++) {
            if (knownRuins[i] == null) {
                continue;
            }
            MapLocation ruin = knownRuins[i];

            int minDistanceToTower = Integer.MAX_VALUE;
            for (int j = 0; j < towerCount; j++) {
                int distance = ruin.distanceSquaredTo(alliedTowers[i]);
                if (distance < minDistanceToTower) {
                    minDistanceToTower = distance;
                }
            }

            if (minDistanceToTower > maxDistanceFromTowers) {
                maxDistanceFromTowers = minDistanceToTower;
                bestRuin = ruin;
            }
        }

        // Kalau ada bestRuin
        if (bestRuin != null) {
            if (rc.isMovementReady()) { // Gerak ke dia
                smartMoveTo(rc, bestRuin);
            }
            if (myLocation.distanceSquaredTo(bestRuin) <= 8) { // Kalau udah deket, coba bangun tower
                MapInfo ruinInfo = null;
                if (rc.canSenseLocation(bestRuin)) {
                    ruinInfo = rc.senseMapInfo(bestRuin);
                }
                if (ruinInfo != null) {
                    buildTowerAtRuin(rc, bestRuin);
                }
            }
            if (rc.isActionReady()) { // Kalau action belum dipakai buat bangun tower, cat tile nya aja
                paintCurrentTile(rc);
                return;
            }
        }

        // Prioritas 4: kalau gaada ruin di memory, kita explore
        if (rc.isActionReady()) {
            paintCurrentTile(rc); // Sambil explore sambil ngecat
        }
        if (rc.isMovementReady()) {
            greedyExplore(rc);
        }
    }
}