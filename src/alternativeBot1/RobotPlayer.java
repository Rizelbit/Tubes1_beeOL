package alternativeBot1;

import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

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
            moveToTarget(rc, nearest); // Gerak ke arah tower
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
        for (MapInfo tile : rc.senseNearbyMapInfos()) { // Scan semua tile dalam jangkauan
            if (!tile.hasRuin()) { // Cari yang punya ruin
                continue;
            }
            MapLocation location = tile.getMapLocation();
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
                int distance = ruin.distanceSquaredTo(alliedTowers[j]);
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
            if (rc.isMovementReady()) { // Gerak ke si bestRuin
                moveToTarget(rc, bestRuin); 
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
            }
            return;
        }

        // Prioritas 4: kalau gaada ruin di memory, kita explore
        if (rc.isActionReady()) {
            paintCurrentTile(rc); // Sambil explore sambil ngecat
        }
        if (rc.isMovementReady()) {
            exploreMap(rc);
        }
    }

    /* ===== SOLDIER HELPERS ===== */
    // Pakai koordinat ruin yang mau dibangun tower di atasnya
    static void buildTowerAtRuin(RobotController rc, MapLocation ruinLocation) throws GameActionException {
        UnitType towerType = UnitType.LEVEL_ONE_PAINT_TOWER; // Summon satu buah tower level 1 tipe paint

        // Cek pattern buat tower udah jadi atau belum
        if (rc.canCompleteTowerPattern(towerType, ruinLocation)) {
            rc.completeTowerPattern(towerType, ruinLocation);
            return;
        }

        // Nempatin penanda di 24 tile sekitar ruin buat nandain pola
        if (rc.canMarkTowerPattern(towerType, ruinLocation)) {
            rc.markTowerPattern(towerType, ruinLocation);
        }

        // Warnain tile sesuai pola pakai warna primer dan sekunder
        if (rc.isActionReady()) {
            for (MapInfo patternTile: rc.senseNearbyMapInfos(ruinLocation, 8)) { // Scan map dalam radius <= 8 dari pusat ruin
                // Cari tile yang udah ditandain dan belum diwarnain sesuai penanda
                if (patternTile.getMark() != PaintType.EMPTY && patternTile.getMark() != patternTile.getPaint()) {
                    boolean useSecondaryColor = (patternTile.getMark() == PaintType.ALLY_SECONDARY);
                    if (rc.canAttack(patternTile.getMapLocation())) {
                        rc.attack(patternTile.getMapLocation(), useSecondaryColor);
                        break;
                    }
                }
            }
        }
        // Kalau action udah kepake, deketin ruin aja terus
        if (rc.isMovementReady()) {
            moveToTarget(rc, ruinLocation);
        }
    }

    // Buat beneran naro cat ke tile di map
    static void paintCurrentTile(RobotController rc) throws GameActionException {
        MapInfo currentTile = rc.senseMapInfo(rc.getLocation()); // Tile yang lagi ditempatin sekarang
        if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
            rc.attack(rc.getLocation()); // Warnain tile yang lagi diinjek sekarang
        }
    }

    /* ===== SPLASHER LOGIC ===== */
    static void runSplasher(RobotController rc) throws GameActionException {
        // Prioritas 1: reload cat kalau hampir habis
        if (tryReloadPaint(rc)) {
            return;
        }

        // Prioritas 2: samperin tower yang butuh support
        if (rc.isMovementReady()) {
            MapLocation target = findTowerToSupport(rc); // Butuh support: tower yang sekitarnya masih belum banyak diwarnain
            if (target != null) { // Kalau ada, samperin
                moveToTarget(rc, target);
            } else { // Kalau gaada, lanjut explore
                exploreMap(rc);
            }
        }

        // Prioritas 3: Area of Effect (AoE) attack
        if (rc.isActionReady()) {
            MapLocation bestTarget = null;
            int maxScore = 0;
            // Cari semua kemungkinan titik yang bisa dijadikan pusat serangan
            for (MapInfo tile : rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared)) {
                MapLocation location = tile.getMapLocation();
                if (!rc.canAttack(location)) {
                    continue;
                }
                int score = 0;
                // Buat setiap kemungkinan, cek berapa tile yang bakal diwarnain kalau diserang di situ
                for (MapInfo aoeTile : rc.senseNearbyMapInfos(location, 4)) {
                    // Aturan scoring: +2 = tile musuh, +1 = bisa diwarnain, +0 = tidak perlu diwarnain ulang, +0 = wall/ruin
                    if (!aoeTile.isWall() && !aoeTile.hasRuin() && !aoeTile.getPaint().isAlly()) {
                        if (aoeTile.getPaint().isEnemy()) {
                            score = score + 2;
                        } else {
                            score = score + 1;
                        }
                    }
                }
                if (score > maxScore) {
                    maxScore = score;
                    bestTarget = location; // Koordinat yang kalau jadi titik pusat serangan, bisa ngecover banyak area
                }
            }
            if (bestTarget != null && maxScore > 0) { // Serang kalo worth it aja
                rc.attack(bestTarget);
            }
        }
    }

    /* ===== SPLASHER HELPERS ===== */
    // Cari tower sekutu yang wilayah sekitarnya paling banyak belum diwarnain
    static MapLocation findTowerToSupport(RobotController rc) throws GameActionException {
        MapLocation best = null;
        int maxUnpainted = 0; // Thresholder jumlah petak belum diwarnain terbanyak

        for (int i = 0; i < towerCount; i++) {
            MapLocation tower = alliedTowers[i];
            if (!rc.canSenseLocation(tower)) { // Kalau di luar jangkauan
                continue;
            }

            int unpaintedCount = 0; // Banyak tile yang belum diwarnain di sekitar tower ini
            for (MapInfo tile : rc.senseNearbyMapInfos(tower, 8)) {
                if (!tile.isWall() && !tile.hasRuin() && !tile.getPaint().isAlly()) {
                    unpaintedCount++; // Nambah terus kalau belum tile itu bukan wall/ruin dan belum diwarnain sekutu
                }
            }

            if (unpaintedCount > maxUnpainted) {
                maxUnpainted = unpaintedCount;
                best = tower;
            }
        }
        return best;
    }

    /* ===== MOPPER LOGIC ===== */
    static void runMopper(RobotController rc) throws GameActionException {
        // Reload cat kalau bener-bener hampir habis aja
        if (rc.getPaint() < 10 && tryReloadPaint(rc)) {
            return;
        }

        // Mopper udah ngelakuin suatu aksi belum di round ini
        boolean acted = false;
        
        // Prioritas 1: mopper swing kalau ada musuh di sekitar
        if (rc.isActionReady()) {
            for (Direction dir : directions) {
                if (rc.canMopSwing(dir)) {
                    rc.mopSwing(dir); // Kalau ada musuh di sekitar (2 petak lurus depan, 2 petak sebelah kirinya, dan 2 petak sebelah kanannya), swing ke arah dia
                    acted = true; // Aksi udah kepake sekali
                    break;
                }
            }
        }

        // Prioritas 2: isi cat ke sekutu yang hampir habis
        if (rc.isActionReady() && !acted && rc.getPaint() > 50) { // Kalau cat mopper masih cukup dan belum beraksi di round ini
            for (RobotInfo ally : rc.senseNearbyRobots(2, rc.getTeam())) { // Cek ada robot sekutu di tile sebelah apa ngga
                if (ally.getPaintAmount() < ally.getType().paintCapacity * 0.2) { 
                    if (rc.canTransferPaint(ally.getLocation(), 20)) {
                        rc.transferPaint(ally.getLocation(), 20); // Mopper memberi 20 cat ke robot sekutu (hence the +)
                        acted = true; // Udah beraksi di round ini
                        break;
                    }
                }
            }
        }

        // Prioritas 3: hapus cat musuh
        if (rc.isActionReady() && !acted) {
            MapLocation bestTarget = null;
            int maxScore = 0;
            for (MapInfo tile : rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared)) {
                if (!tile.getPaint().isEnemy() || !rc.canAttack(tile.getMapLocation())) {
                    continue;
                }
                // Sistem scoring: default kalau tile punya musuh = 1, +2 = kalau tile sekutu sebelahan sama tile musuh (perbatasan)
                int score = 1;
                for (MapInfo neighbor : rc.senseNearbyMapInfos(tile.getMapLocation(), 2)) {
                    if (neighbor.getPaint().isAlly()) {
                        score = score + 2;
                    }
                }
                if (score > maxScore) {
                    maxScore = score;
                    bestTarget = tile.getMapLocation();
                }
            }
            if (bestTarget != null) {
                rc.attack(bestTarget);
            }
        }
        if (rc.isMovementReady()) {
            patrolAllyTerritory(rc);
        }
    }

    /* ===== MOPPER HELPERS ===== */
    static void patrolAllyTerritory(RobotController rc) throws GameActionException {
        Direction bestDirection = null;
        int maxScore = -99999;

        for (Direction dir : directions) {
            if (!rc.canMove(dir)) {
                continue;
            }
            MapLocation nextLocation = rc.getLocation().add(dir);
            int score = 0;

            if (nextLocation.x > 0 && nextLocation.x < 61 && nextLocation.y >= 0 && nextLocation.y < 61) {
                int tileType = mapMemory[nextLocation.x][nextLocation.y]; // Tile yang mau dituju
                if (tileType == 2) { // +30 = tile sekutu, boleh tapi ga prioritas utama
                    score = score + 30;
                } else if (tileType == 3) { // +50 = tile musuh (mau dihapus catnya)
                    score = score + 50;
                } else if (tileType == 1) { // +10 = tile kosong, bisa dilewatin aja tapi ga prioritas juga
                    score = score + 10; 
                }
            }

            // Biar ga jalan ke arah tile yang baru dikunjungi, kasih -100 pas mau mencoba jalan ke arah itu
            for (int i = 0; i < visitedTiles.length; i++) {
                if (visitedTiles[i] != null && visitedTiles[i].equals(nextLocation)) {
                    score = score - 100;
                }
            }

            // Biar arah geraknya ngacak (kepake di kalau ada 2 atau lebih arah yang scorenya sama)
            score = score + randomNumberGenerator.nextInt(5);
            if (score > maxScore) {
                maxScore = score;
                bestDirection =  dir;
            }
        }
        
        // Perbarui lit koordinat tile yang udah dilewatin
        if (bestDirection != null) {
            rc.move(bestDirection);
            visitedTiles[visitedIndex] = rc.getLocation();
            visitedIndex = (visitedIndex + 1) % visitedTiles.length; // Kalau list udah penuh, balik lagi timpa yang awal
        }
    }

    /* ===== MOVEMENT HELPERS ===== */
    // Dipakai kalau robot punya tujuan spesifik mau ke mana
    static void moveToTarget(RobotController rc, MapLocation target) throws GameActionException {
        Direction bestDirection = null;
        int minScore = Integer.MAX_VALUE;

        for (Direction dir : directions) {
            if (!rc.canMove(dir)) {
                continue;
            }

            MapLocation nextLocation = rc.getLocation().add(dir);
            int distance = nextLocation.distanceSquaredTo(target);
            int penalty = 0;

            // Pilih arah dengan jarak paling dekat ke target sambil ngehindarin tile yang baru aja dilewatin
            for (int i = 0; i < visitedTiles.length; i++) {
                if (visitedTiles[i] != null && visitedTiles[i].equals(nextLocation)) {
                    penalty = penalty + 1000;
                }
            }
            if (distance + penalty < minScore) {
                minScore = distance + penalty;
                bestDirection = dir;
            }
        }

        // Perbaru list tile yang baru dilewatin
        if (bestDirection != null) {
            rc.move(bestDirection);
            visitedTiles[visitedIndex] = rc.getLocation();
            visitedIndex = (visitedIndex + 1) % visitedTiles.length;
        }
    }

    // Dipakai kalau robot belum punya tujuan spesifik mau ke mana (emang buat explore aja nyari-nyari)
    static void exploreMap(RobotController rc) throws GameActionException {
        Direction bestDirection = null;
        int maxScore = -99999;

        for (Direction dir : directions) {
            if (!rc.canMove(dir)) {
                continue;
            }
            MapLocation nextLocation = rc.getLocation().add(dir);
            int score = 0;

            if (nextLocation.x >= 0 && nextLocation.x < 61 && nextLocation.y >= 0 && nextLocation.y < 61) {
                int tileType = mapMemory[nextLocation.x][nextLocation.y];
                if (tileType == 0) { // Kalau ketemu tile unknown, ada peluang nemu ruin
                    score = score + 100;
                } else if (tileType == 1) { // Kalau ketemu tile kosong, bisa diwarnain
                    score = score + 50;
                } else if (tileType == 3) { // Kalau ketemu tile musuh, bisa direbut tapi ga high prio
                    score = score + 25;
                } else if (tileType == 2) { // Kalau ketemu tile sekutu, gausah diapa2in lagi
                    score = score - 75;
                } else if (tileType == 4) { // Kalau ketemu tembok/ruin, gak bakal dipilih
                    score = score - 999;
                }
            }

            // Biar ga balik lagi ke tile yang udah pernah disamperin
            for (int i = 0; i < visitedTiles.length; i++) {
                if (visitedTiles[i] != null && visitedTiles[i].equals(nextLocation)) {
                    score = score - 150;
                }
            }

            // Biar arah geraknya random
            score = score + randomNumberGenerator.nextInt(10);
            if (score > maxScore) {
                maxScore = score;
                bestDirection = dir;
            }
        }

        // Perbaruin list
        if (bestDirection != null) {
            rc.move(bestDirection);
            visitedTiles[visitedIndex] = rc.getLocation();
            visitedIndex = (visitedIndex + 1) % visitedTiles.length;
        }
    }
}