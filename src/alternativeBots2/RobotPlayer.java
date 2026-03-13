package alternativeBots2;
// package argha;

import battlecode.common.*;

// import java.util.Arrays;
// import java.util.HashMap;
// import java.util.HashSet;
// import java.util.Map;
import java.util.Random;
// import java.util.Set;


/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public class RobotPlayer {
    /**
     * We will use this variable to count the number of turns this robot has been alive.
     * You can use static variables like this to save any information you want. Keep in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between your robots.
     */
    static int turnCount = 0;

    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very useful for debugging!
     */
    static final Random rng = new Random(6147);

    /** Array containing all the possible movement directions. */
    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    // PENJELASAN: Counter untuk mengatur giliran spawn unit di tower (dipakai sistem 3 fase).
    // Setiap kali tower berhasil spawn unit, counter ini bertambah satu.
    static int spawnCounter = 0;

    // PENJELASAN: Circular buffer untuk menyimpan 30 lokasi terakhir yang dikunjungi robot.
    // Digunakan sebagai mekanisme anti-loop agar robot tidak berputar di tempat yang sama.
    static MapLocation[] visitedTiles = new MapLocation[30];
    static int visitedIndex = 0;

    // PENJELASAN: Array untuk menyimpan lokasi tower sekutu yang pernah terlihat oleh robot.
    // Digunakan agar robot bisa kembali ke tower untuk isi ulang cat meskipun tower sudah berada di luar jangkauan sensor saat ini.
    static MapLocation[] knownTowers = new MapLocation[30];
    static int knownTowerCount = 0;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * It is like the main function for your robot. If this method returns, the robot dies!
     *
     * @param rc  The RobotController object. You use it to perform actions from this robot, and to get
     *            information on its current status. Essentially your portal to interacting with the world.
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        // Hello world! Standard output is very useful for debugging.
        // Everything you say here will be directly viewable in your terminal when you run a match!
        System.out.println("I'm alive");

        // You can also use indicators to save debug notes in replays.
        rc.setIndicatorString("Hello world!");

        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to do.

            turnCount += 1;  // We have now been alive for one more turn!

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode.
            try {
                // The same run() function is called for every robot on your team, even if they are
                // different types. Here, we separate the control depending on the UnitType, so we can
                // use different strategies on different robots. If you wish, you are free to rewrite
                // this into a different control structure!

                // PENJELASAN: Setiap giliran, perbarui daftar tower sekutu yang diketahui agar robot selalu tahu ke mana harus kembali untuk isi ulang cat.
                rememberTowers(rc);

                switch (rc.getType()){
                    case SOLDIER: runSoldier(rc); break; 
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: runSplasher(rc); break; // Consider upgrading examplefuncsplayer to use splashers!
                    default: runTower(rc); break;
                    }
                }
             catch (GameActionException e) {
                // Oh no! It looks like we did something illegal in the Battlecode world. You should
                // handle GameActionExceptions judiciously, in case unexpected events occur in the game
                // world. Remember, uncaught exceptions cause your robot to explode!
                System.out.println("GameActionException");
                e.printStackTrace();

            } catch (Exception e) {
                // Oh no! It looks like our code tried to do something bad. This isn't a
                // GameActionException, so it's more likely to be a bug in our code.
                System.out.println("Exception");
                e.printStackTrace();

            } finally {
                // Signify we've done everything we want to do, thereby ending our turn.
                // This will make our code wait until the next turn, and then perform this loop again.
                Clock.yield();
            }
            // End of loop: go back to the top. Clock.yield() has ended, so it's time for another turn!
        }

        // Your code should never reach here (unless it's intentional)! Self-destruction imminent...
    }

    /**
     * Run a single turn for towers.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runTower(RobotController rc) throws GameActionException{
        // PENJELASAN: Upgrade tower sesegera mungkin agar menghasilkan lebih banyak cat per ronde dan memiliki damage lebih tinggi. Upgrade adalah investasi jangka panjang.
        if (rc.canUpgradeTower(rc.getLocation())) {
            rc.upgradeTower(rc.getLocation());
        }

        // PENJELASAN: Tower menyerang musuh yang terdeteksi di sekitarnya terlebih dahulu sebelum melakukan aksi lain. Ini melindungi area spawn dari serangan musuh.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (rc.canAttack(enemy.location)) {
                rc.attack(enemy.location);
                break;
            }
        }

        // PENJELASAN: Tower secara aktif mengisi ulang cat robot sekutu yang cadangan catnya sudah di bawah 1/3 kapasitas maksimal. Ini mencegah robot kehabisan cat di lapangan dan terkena cooldown penalty akibat berdiri di wilayah netral.
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.type == UnitType.SOLDIER || ally.type == UnitType.MOPPER || ally.type == UnitType.SPLASHER) {
                if (ally.paintAmount < ally.type.paintCapacity / 3) {
                    int give = Math.min(rc.getPaint() / 4, ally.type.paintCapacity - ally.paintAmount);
                    if (give > 5 && rc.canTransferPaint(ally.location, give)) {
                        rc.transferPaint(ally.location, give);
                        break;
                    }
                }
            }
        }

        // PENJELASAN: Hitung jumlah petak cat musuh di sekitar tower sebagai indikator ancaman.
        // Jika lebih dari 8 petak musuh terdeteksi, tower akan spawn mopper secara darurat untuk membersihkan cat musuh sebelum menyebar lebih luas.
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        int enemyPaintCount = 0;
        for (MapInfo tile : nearbyTiles) {
            if (tile.getPaint().isEnemy()) {
                enemyPaintCount++;
            }
        }

        // PENJELASAN: Tower tidak spawn unit jika cadangan catnya di bawah 200.
        // Ini menghemat cat untuk supply ke robot sekutu dan regenerasi alami tower.
        if (rc.getPaint() < 200) {
            // Read incoming messages
            Message[] messages = rc.readMessages(-1);
            for (Message m : messages) {
                System.out.println("Tower received message: '#" + m.getSenderID() + " " + m.getBytes());
            }
            // TODO: can we attack other bots?
            return;
        }

        // PENJELASAN: Sistem spawn 3 fase berdasarkan nomor ronde untuk mengoptimalkan komposisi pasukan.
        // - Fase 1 (ronde 0-100)  : PURE soldier untuk ekspansi wilayah secepat mungkin. Splasher TIDAK dispawn karena belum ada wilayah musuh untuk direclaim. Splasher mahal (400 chips + 300 paint) dan tidak efektif di awal game.
        // - Fase 2 (ronde 100-300): Mulai campurkan splasher setelah ada wilayah musuh. Rasio: 40% splasher, 50% soldier, 10% mopper.
        // - Fase 3 (ronde 300+)   : Splasher-heavy untuk mendominasi dan reclaim wilayah musuh. Rasio: 60% splasher, 20% soldier, 20% mopper.
        // Jika banyak cat musuh terdeteksi (> 8), selalu spawn mopper terlebih dahulu.
        UnitType toSpawn;
        if (enemyPaintCount > 8) {
            // PENJELASAN: Kondisi darurat, terlalu banyak cat musuh di sekitar, spawn mopper untuk membersihkan ancaman aktif segera.
            toSpawn = UnitType.MOPPER;
        } else if (rc.getRoundNum() < 100) {
            // PENJELASAN: Fase 1, pure soldier untuk ekspansi cepat.
            // Di awal game, setiap chips harus diinvestasikan ke unit yang langsung bisa mewarnai wilayah.
            // Splasher tidak ada gunanya jika belum ada cat musuh untuk dihapus.
            toSpawn = UnitType.SOLDIER;
        } else if (rc.getRoundNum() < 300) {
            // PENJELASAN: Fase 2, mulai campurkan splasher untuk reclaim wilayah musuh.
            // Rasio: 50% soldier (tetap ekspansi), 40% splasher (mulai reclaim), 10% mopper.
            int r = spawnCounter % 10;
            if (r < 4) toSpawn = UnitType.SPLASHER;
            else if (r < 9) toSpawn = UnitType.SOLDIER;
            else toSpawn = UnitType.MOPPER;
        } else {
            // PENJELASAN: Fase 3, splasher dominasi untuk merebut kembali wilayah musuh secara masif.
            // Rasio: 60% splasher, 20% soldier, 20% mopper.
            int r = spawnCounter % 10;
            if (r < 6) toSpawn = UnitType.SPLASHER;
            else if (r < 8) toSpawn = UnitType.SOLDIER;
            else toSpawn = UnitType.MOPPER;
        }

        // PENJELASAN: Pilih lokasi spawn terbaik secara greedy berdasarkan situasi lapangan.
        // Skor lokasi spawn = jumlah cat musuh di sekitar lokasi - jumlah robot sekutu di sekitar.
        // Ini memastikan unit baru dispawn ke arah yang paling membutuhkan perhatian, bukan ke arah yang sudah ramai oleh robot sekutu sendiri (menghindari penumpukan).
        Direction bestSpawnDir = null;
        int bestSpawnScore = Integer.MIN_VALUE;
        for (Direction d : directions) {
            MapLocation spawnLoc = rc.getLocation().add(d);
            if (!rc.canBuildRobot(toSpawn, spawnLoc)) continue;

            int spawnScore = 0;
            for (MapInfo t : nearbyTiles) {
                if (spawnLoc.distanceSquaredTo(t.getMapLocation()) <= 4) {
                    if (t.getPaint().isEnemy()) spawnScore++;
                }
            }
            RobotInfo[] allyNearSpawn = rc.senseNearbyRobots(spawnLoc, 4, rc.getTeam());
            spawnScore -= allyNearSpawn.length;

            if (spawnScore > bestSpawnScore) {
                bestSpawnScore = spawnScore;
                bestSpawnDir = d;
            }
        }
        if (bestSpawnDir != null) {
            rc.buildRobot(toSpawn, rc.getLocation().add(bestSpawnDir));
            spawnCounter++;
        }

        // Read incoming messages
        Message[] messages = rc.readMessages(-1);
        for (Message m : messages) {
            System.out.println("Tower received message: '#" + m.getSenderID() + " " + m.getBytes());
        }

        // TODO: can we attack other bots?
    }


    /**
     * Run a single turn for a Soldier.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runSoldier(RobotController rc) throws GameActionException{
        MapLocation myLoc = rc.getLocation();
        int curPaint = rc.getPaint();
        int maxPaint = rc.getType().paintCapacity;

        // Sense information about all visible nearby tiles.
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        // PENJELASAN: Soldier hanya mencari ruins yang SANGAT DEKAT (distanceSquared <= 4).
        // Soldier berperan sebagai "Border Guard" (penjaga perbatasan), bukan "Tower Hunter".
        // Jika soldier terus mengejar ruins yang jauh, dia meninggalkan wilayah sekutu tanpa penjagaan. Dengan batasan ini, soldier hanya membangun tower jika kebetulan lewat dekat ruins.
        // Search for a nearby ruin to complete.
        MapLocation nearestRuin = null;
        int nearestRuinDist = Integer.MAX_VALUE;
        for (MapInfo tile : nearbyTiles){
            if (tile.hasRuin()){
                MapLocation ruinLoc = tile.getMapLocation();
                RobotInfo robotAtRuin = rc.senseRobotAtLocation(ruinLoc);
                if (robotAtRuin == null) {
                    int dist = myLoc.distanceSquaredTo(ruinLoc);
                    // PENJELASAN: Filter ketat, hanya lirik ruins yang distanceSquared <= 4 (artinya hanya 1-2 langkah dari posisi saat ini).
                    if (dist <= 4 && dist < nearestRuinDist) {
                        nearestRuinDist = dist;
                        nearestRuin = ruinLoc;
                    }
                }
            }
        }

        // PENJELASAN: Sistem refill cat yang dinamis, robot mulai balik ke tower saat cat sudah di bawah 30% kapasitas. Ini memastikan robot tidak kehabisan cat di tengah lapangan dan terkena penalti cooldown maupun -20 HP per ronde.
        MapLocation nearestTower = findNearestKnownTower(rc);
        double paintRatio = (double) curPaint / maxPaint;
        if (paintRatio < 0.30 && nearestTower != null) {
            if (myLoc.distanceSquaredTo(nearestTower) <= 2) {
                // PENJELASAN: Robot sudah adjacent ke tower, langsung tarik cat penuh.
                int need = maxPaint - curPaint;
                if (rc.canTransferPaint(nearestTower, -need)) {
                    rc.transferPaint(nearestTower, -need);
                    curPaint = rc.getPaint();
                    paintRatio = (double) curPaint / maxPaint;
                }
            } else if (rc.isMovementReady()) {
                // PENJELASAN: Cat hampir habis tapi belum sampai tower, navigasi ke tower sambil tetap mewarnai petak di jalan agar tidak membuang giliran.
                navigateToward(rc, nearestTower);
                paintCurrentTile(rc);
                return;
            }
        }

        // PENJELASAN: Bangun tower hanya jika ruins kebetulan sangat dekat (distSq <= 4).
        // Soldier tidak mengubah rute perjalanannya khusus untuk mengejar ruins yang jauh.
        if (nearestRuin != null) {
            UnitType tt = chooseTowerType(rc);

            if (nearestRuinDist <= 2) {
                // PENJELASAN: Robot sudah adjacent ke ruins, langsung eksekusi pembangunan tower:
                // 1. Tandai pola 5x5 di sekitar ruins dengan markTowerPattern()
                // 2. Warnai petak-petak yang ditandai sesuai pola warna tower
                // 3. Selesaikan tower dengan completeTowerPattern() jika pola sudah lengkap
                if (rc.canMarkTowerPattern(tt, nearestRuin)) {
                    rc.markTowerPattern(tt, nearestRuin);
                }
                if (rc.canCompleteTowerPattern(tt, nearestRuin)) {
                    rc.completeTowerPattern(tt, nearestRuin);
                    nearestRuin = null;
                } else if (rc.isActionReady()) {
                    // Fill in any spots in the pattern with the appropriate paint.
                    for (MapInfo pt : rc.senseNearbyMapInfos(nearestRuin, 8)) {
                        if (pt.getMark() != pt.getPaint() && pt.getMark() != PaintType.EMPTY) {
                            boolean sec = pt.getMark() == PaintType.ALLY_SECONDARY;
                            if (rc.canAttack(pt.getMapLocation())) {
                                rc.attack(pt.getMapLocation(), sec);
                                break;
                            }
                        }
                    }
                    // Complete the ruin if we can.
                    if (rc.canCompleteTowerPattern(tt, nearestRuin)) {
                        rc.completeTowerPattern(tt, nearestRuin);
                        nearestRuin = null;
                    }
                    // PENJELASAN: Fokus sepenuhnya ke pembangunan tower saat sedang adjacent.
                    // Jangan buang aksi untuk hal lain sampai tower selesai dibangun.
                    return;
                }
            } else {
                // PENJELASAN: Ruins terdeteksi di jarak 3-4 (sangat dekat tapi belum adjacent), navigasi ke sana sambil tetap mewarnai petak di jalan.
                if (rc.isMovementReady()) {
                    navigateToward(rc, nearestRuin);
                    paintCurrentTile(rc);
                    return;
                }
            }
        }

        // PENJELASAN: Sistem pewarnaan dengan prioritas "jaga wilayah", ini adalah heuristic utama bot ini.
        // Skor pewarnaan (greedy, pilih target dengan skor tertinggi):
        // - Skor 5: cat musuh yang BERSEBELAHAN dengan wilayah sekutu = ancaman aktif, hapus segera!
        // - Skor 2: cat musuh yang jauh dari wilayah sekutu = ancaman pasif
        // - Skor 1: petak kosong yang bisa diwarnai = ekspansi biasa
        // - Skor 0: petak sekutu = skip, tidak perlu diwarnai ulang (hemat aksi)
        // Try to paint beneath us as we walk to avoid paint penalties.
        // Avoiding wasting paint by re-painting our own tiles.
        if (rc.isActionReady()) {
            MapInfo curTile = rc.senseMapInfo(rc.getLocation());
            if (!curTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
                // PENJELASAN: Warnai petak di bawah kaki terlebih dahulu agar tidak terkena penalti cat berdiri di wilayah netral/musuh.
                rc.attack(rc.getLocation());
            } else {
                MapLocation bestPaint = null;
                int bestPaintScore = 0;
                for (MapInfo tile : nearbyTiles) {
                    if (!rc.canAttack(tile.getMapLocation())) continue;
                    MapLocation tileLoc = tile.getMapLocation();
                    PaintType tp = tile.getPaint();

                    int s = 0;
                    if (tp.isEnemy()) {
                        // PENJELASAN: Cek apakah petak cat musuh ini bersebelahan dengan wilayah sekutu.
                        // Jika ya, ini adalah "penyusupan aktif" yang harus dihapus segera sebelum musuh semakin dalam ke wilayah kita.
                        boolean nearAlly = false;
                        for (Direction d : directions) {
                            MapLocation adj = tileLoc.add(d);
                            if (rc.canSenseLocation(adj) && rc.senseMapInfo(adj).getPaint().isAlly()) {
                                nearAlly = true;
                                break;
                            }
                        }
                        s = nearAlly ? 5 : 2;
                    } else if (tp == PaintType.EMPTY && tile.isPassable() && !tile.hasRuin()) {
                        s = 1;
                    }

                    if (s > bestPaintScore) {
                        bestPaintScore = s;
                        bestPaint = tileLoc;
                    }
                }
                if (bestPaint != null) rc.attack(bestPaint);
            }
        }

        // PENJELASAN: Sistem gerak greedy "Border Guard", pilih arah dengan skor tertinggi.
        // Fungsi skor: score = (petak_kosong x 12) - (biaya_cat x 15) - (robot_sekutu x 8)
        // - Petak kosong banyak di sekitar tujuan = bagus, banyak yang bisa diwarnai
        // - Biaya cat tinggi (wilayah musuh) = buruk, menghabiskan cat dengan cepat
        // - Banyak robot sekutu di tujuan = buruk, supaya soldier menyebar otomatis
        // PENTING: Tidak ada penalti untuk wilayah sekutum soldier BOLEH tetap di dekat wilayah sendiri karena tugasnya adalah menjaga perbatasan, bukan selalu ekspansi keluar.
        // Move and attack randomly if no objective.
        if (rc.isMovementReady()) {
            Direction bestDir = null;
            int bestScore = Integer.MIN_VALUE;

            for (Direction d : directions) {
                if (!rc.canMove(d)) continue;
                MapLocation dest = myLoc.add(d);
                MapInfo destInfo = rc.senseMapInfo(dest);
                PaintType destPaint = destInfo.getPaint();

                int traversalCost = paintCostOf(destPaint);
                int newTiles = countUnpaintedAround(rc, dest);

                int score = newTiles * 12;
                score -= traversalCost * 15;

                // PENJELASAN: Penalti tambahan untuk jalur berbayar jika cat sedang rendah.
                // Semakin rendah cat, semakin soldier menghindari wilayah netral/musuh.
                if (paintRatio < 0.4) score -= traversalCost * 10;
                if (paintRatio < 0.2 && traversalCost > 0) score -= 30;

                // PENJELASAN: Penalti anti-cluster, hindari arah yang sudah ramai robot sekutu agar soldier menyebar ke area yang berbeda secara otomatis.
                RobotInfo[] allyNear = rc.senseNearbyRobots(dest, 2, rc.getTeam());
                score -= 8 * allyNear.length;

                // PENJELASAN: Penalti anti-loop, kurangi skor untuk lokasi yang baru saja dikunjungi agar robot tidak berputar-putar di tempat yang sama.
                score -= visitedPenalty(dest);
                score += rng.nextInt(3);

                if (score > bestScore) {
                    bestScore = score;
                    bestDir = d;
                }
            }

            if (bestDir != null) {
                rc.move(bestDir);
                recordVisit(rc.getLocation());
            } else {
                // PENJELASAN: Fallback, jika semua arah mendapat skor sama buruk atau tidak valid, gerak secara acak sebagai tindakan darurat agar robot tidak membeku.
                Direction randomDir = directions[rng.nextInt(directions.length)];
                if (rc.canMove(randomDir)) {
                    rc.move(randomDir);
                    recordVisit(rc.getLocation());
                }
            }
        }
    }


    /**
     * Run a single turn for a Mopper.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runMopper(RobotController rc) throws GameActionException{
        MapLocation myLoc = rc.getLocation();
        int curPaint = rc.getPaint();
        int maxPaint = 100;

        // PENJELASAN: Mopper isi ulang cat hanya jika sudah sangat kritis (< 20).
        // Threshold sengaja dibuat rendah karena mopper bisa mendapat cat dari musuh saat menyerang, sehingga tidak perlu sering balik ke tower seperti soldier.
        if (curPaint < 20) {
            MapLocation tower = findNearestKnownTower(rc);
            if (tower != null) {
                if (myLoc.distanceSquaredTo(tower) <= 2) {
                    int need = maxPaint - curPaint;
                    if (rc.canTransferPaint(tower, -need)) {
                        rc.transferPaint(tower, -need);
                    }
                } else {
                    navigateToward(rc, tower);
                    return;
                }
            }
        }

        // PENJELASAN: Mopper membantu soldier yang catnya hampir habis (< 50% kapasitas = 100 cat).
        // Transfer dilakukan jika mopper sendiri masih punya cukup cat (sisakan minimal 10).
        // Ini mencegah soldier terkena cooldown penalty akibat kekurangan cat di lapangan.
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.type == UnitType.SOLDIER && ally.paintAmount < 100) {
                if (myLoc.distanceSquaredTo(ally.location) <= 2) {
                    int transfer = Math.min(curPaint - 10, 200 - ally.paintAmount);
                    if (transfer > 0 && rc.canTransferPaint(ally.location, transfer)) {
                        rc.transferPaint(ally.location, transfer);
                        curPaint -= transfer;
                        break;
                    }
                }
            }
        }

        // PENJELASAN: Mop Swing adalah serangan AoE mopper yang mengenai 3 petak sekaligus dalam satu arah (depan, depan-kiri, depan-kanan). Hanya dilakukan jika ada MINIMAL 2 musuh berjejer di arah tersebut agar tidak membuang cooldown 20 ronde untuk target yang terlalu sedikit.
        // Move and attack randomly.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        boolean didSwing = false;
        for (Direction d : directions) {
            if (rc.canMopSwing(d)) {
                int enemiesInSwing = countEnemiesInSwingDir(rc, d, enemies);
                if (enemiesInSwing >= 2) {
                    rc.mopSwing(d);
                    System.out.println("Mop Swing! Booyah!");
                    didSwing = true;
                    break;
                }
            }
        }

        // PENJELASAN: Sistem gerak mopper menuju area cat musuh terbanyak.
        // Skor arah gerak (greedy, pilih arah dengan skor tertinggi):
        // - Cat musuh di tujuan: +20 (mopper ingin berada di wilayah musuh untuk menyerang)
        // - Biaya cat traversal x 8: negatif (hindari jalur mahal)
        // - Wilayah sekutu: -15 (dorong mopper keluar dari zona aman menuju musuh)
        // - Robot musuh di sekitar: +25 per robot (bisa drain cat musuh saat menyerang)
        // - Lookahead 1 petak: +10 jika petak berikutnya juga cat musuh (area musuh lebih luas)
        if (rc.isMovementReady()) {
            Direction bestDir = null;
            int bestScore = Integer.MIN_VALUE;

            for (Direction d : directions) {
                if (!rc.canMove(d)) continue;
                MapLocation dest = myLoc.add(d);
                MapInfo destInfo = rc.senseMapInfo(dest);
                PaintType destPaint = destInfo.getPaint();

                int score = 0;
                int traversalCost = paintCostOf(destPaint);

                score -= traversalCost * 8;
                if (destPaint.isEnemy()) score += 20;
                if (destPaint.isAlly()) score -= 15;

                for (RobotInfo enemy : enemies) {
                    if (dest.distanceSquaredTo(enemy.location) <= 4) {
                        score += 25;
                    }
                }

                // PENJELASAN: Lookahead 1 petak ke depan, jika petak setelah tujuan juga cat musuh, artinya ada area musuh yang lebih luas di arah itu, prioritaskan arah tersebut.
                MapLocation further = dest.add(d);
                if (rc.canSenseLocation(further)) {
                    if (rc.senseMapInfo(further).getPaint().isEnemy()) score += 10;
                }

                score -= visitedPenalty(dest);
                score += rng.nextInt(3);

                if (score > bestScore) {
                    bestScore = score;
                    bestDir = d;
                }
            }

            if (bestDir != null) {
                rc.move(bestDir);
                recordVisit(rc.getLocation());
            } else {
                Direction randomDir = directions[rng.nextInt(directions.length)];
                if (rc.canMove(randomDir)) {
                    rc.move(randomDir);
                    recordVisit(rc.getLocation());
                }
            }
        }

        myLoc = rc.getLocation();

        // PENJELASAN: Setelah bergerak, coba mop swing lagi dengan threshold lebih rendah (>= 1).
        // Sebelum bergerak threshold >= 2, setelah bergerak cukup 1 musuh sudah worth it karena posisi robot sudah lebih dekat ke musuh.
        if (!didSwing) {
            enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            for (Direction d : directions) {
                if (rc.canMopSwing(d)) {
                    int cnt = countEnemiesInSwingDir(rc, d, enemies);
                    if (cnt >= 1) {
                        rc.mopSwing(d);
                        System.out.println("Mop Swing! Booyah!");
                        didSwing = true;
                        break;
                    }
                }
            }
        }

        // PENJELASAN: Jika tidak bisa mop swing, serang satu petak cat musuh terdekat secara langsung.
        // Ini menghapus cat musuh dari peta satu per satu secara konsisten meskipun tidak ada robot musuh yang bisa di-drain.
        if (!didSwing) {
            for (Direction d : directions) {
                MapLocation target = myLoc.add(d);
                if (rc.canSenseLocation(target)) {
                    MapInfo targetInfo = rc.senseMapInfo(target);
                    if (targetInfo.getPaint().isEnemy() && rc.canAttack(target)) {
                        rc.attack(target);
                        break;
                    }
                }
            }
        }

        // We can also move our code into different methods or classes to better organize it!
        updateEnemyRobots(rc);
    }

    /**
     * Run a single turn for a Splasher.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runSplasher(RobotController rc) throws GameActionException{
        MapLocation myLoc = rc.getLocation();
        int curPaint = rc.getPaint();
        int maxPaint = rc.getType().paintCapacity;

        // PENJELASAN: Splasher mulai balik ke tower jika cat sudah di bawah 25% kapasitas.
        // Threshold lebih tinggi dari mopper karena setiap splash menghabiskan banyak cat dan splasher harus selalu punya cadangan cukup untuk splash yang efektif.
        double paintRatio = (double) curPaint / maxPaint;
        if (paintRatio < 0.25) {
            MapLocation tower = findNearestKnownTower(rc);
            if (tower != null) {
                if (myLoc.distanceSquaredTo(tower) <= 2) {
                    int need = maxPaint - curPaint;
                    if (rc.canTransferPaint(tower, -need)) {
                        rc.transferPaint(tower, -need);
                        curPaint = rc.getPaint();
                        paintRatio = (double) curPaint / maxPaint;
                    }
                } else if (rc.isMovementReady()) {
                    navigateToward(rc, tower);
                    return;
                }
            }
        }

        // PENJELASAN: Splasher bergerak menuju area dengan cat musuh terbanyak di sekitarnya.
        // Splasher adalah "Area Reclaimer", tugasnya merebut kembali wilayah yang sudah diambil musuh, bukan ekspansi ke area baru yang masih kosong.
        // Skor arah gerak:
        // - Cat musuh di sekitar tujuan x 15, prioritas utama, menuju area musuh terpadat
        // - Biaya cat traversal x 12, negatif, hemat cat di perjalanan
        // - Petak musuh: +10, Petak kosong: +5, bonus kecil untuk hemat cat traversal
        if (rc.isMovementReady()) {
            Direction bestDir = null;
            int bestMoveScore = Integer.MIN_VALUE;

            for (Direction d : directions) {
                if (!rc.canMove(d)) continue;
                MapLocation dest = myLoc.add(d);
                MapInfo destInfo = rc.senseMapInfo(dest);
                PaintType destPaint = destInfo.getPaint();

                int traversalCost = paintCostOf(destPaint);
                int score = 0;
                score -= traversalCost * 12;

                // PENJELASAN: Hitung jumlah cat musuh di 8 arah sekitar tujuan.
                // Splasher ingin bergerak ke area dengan konsentrasi cat musuh tertinggi karena splash di sana akan mereclaim banyak wilayah sekaligus.
                int enemyPaintAround = countEnemyPaintAround(rc, dest);
                score += enemyPaintAround * 15;

                if (destPaint.isEnemy()) score += 10;
                if (destPaint == PaintType.EMPTY) score += 5;

                if (paintRatio < 0.3) score -= traversalCost * 10;

                score -= visitedPenalty(dest);
                score += rng.nextInt(3);

                if (score > bestMoveScore) {
                    bestMoveScore = score;
                    bestDir = d;
                }
            }

            if (bestDir != null) {
                rc.move(bestDir);
                recordVisit(rc.getLocation());
            } else {
                Direction randomDir = directions[rng.nextInt(directions.length)];
                if (rc.canMove(randomDir)) {
                    rc.move(randomDir);
                    recordVisit(rc.getLocation());
                }
            }
        }

        // PENJELASAN: Pilih titik splash terbaik dalam jangkauan aksi secara greedy.
        // Fungsi skor splash: score = (4 x cat_musuh) + (2 x petak_kosong) - (2 x petak_sekutu)
        // - Cat musuh dapat bobot 4 (tertinggi), tujuan utama: reclaim wilayah musuh
        // - Petak kosong dapat bobot 2, bisa sekalian diwarnai saat reclaim
        // - Petak sekutu mendapat penalti -2, splash di wilayah sendiri membuang cat sia-sia
        if (rc.isActionReady()) {
            myLoc = rc.getLocation();
            MapInfo[] allNearby = rc.senseNearbyMapInfos();
            MapLocation bestSplash = null;
            int bestSplashScore = 0;

            MapInfo[] attackCandidates = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
            for (MapInfo candidate : attackCandidates) {
                MapLocation loc = candidate.getMapLocation();
                if (!rc.canAttack(loc)) continue;

                int enemyPaint = 0;
                int empty = 0;
                int allyPaint = 0;

                // PENJELASAN: Evaluasi semua petak dalam radius splash (distanceSquared <= 4) dari titik pusat kandidat untuk menghitung total nilai splash di titik tersebut.
                for (MapInfo t : allNearby) {
                    if (loc.distanceSquaredTo(t.getMapLocation()) <= 4) {
                        PaintType p = t.getPaint();
                        if (p.isEnemy()) enemyPaint++;
                        else if (p == PaintType.EMPTY) empty++;
                        else if (p.isAlly()) allyPaint++;
                    }
                }

                int score = 4 * enemyPaint + 2 * empty - 2 * allyPaint;
                if (score > bestSplashScore) {
                    bestSplashScore = score;
                    bestSplash = loc;
                }
            }

            if (bestSplash != null) {
                rc.attack(bestSplash);
            }
        }
    }

    public static void updateEnemyRobots(RobotController rc) throws GameActionException{
        // Sensing methods can be passed in a radius of -1 to automatically use the largest possible value.
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemyRobots.length != 0){
            rc.setIndicatorString("There are nearby enemy robots! Scary!");
            // Save an array of locations with enemy robots in them for possible future use.
            MapLocation[] enemyLocations = new MapLocation[enemyRobots.length];
            for (int i = 0; i < enemyRobots.length; i++){
                enemyLocations[i] = enemyRobots[i].getLocation();
            }
            RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
            // Occasionally try to tell nearby allies how many enemy robots we see.
            if (rc.getRoundNum() % 20 == 0){
                for (RobotInfo ally : allyRobots){
                    if (rc.canSendMessage(ally.location, enemyRobots.length)){
                        rc.sendMessage(ally.location, enemyRobots.length);
                    }
                }
            }
        }
    }

    // HELPER FUNCTIONS
    // HELPER FUNCTIONS
    // HELPER FUNCTIONS

    // PENJELASAN: Pilih tipe tower yang akan dibangun berdasarkan jumlah tower yang sudah ada.
    // - Tower ke-1 dan ke-2: Paint Tower, prioritas supply cat di awal game
    // - Setiap kelipatan 3 tower: Money Tower, tambah income chips per ronde
    // - Sisanya: Paint Tower, jaga pasokan cat tetap melimpah di seluruh fase permainan
    static UnitType chooseTowerType(RobotController rc) {
        int n = rc.getNumberTowers();
        if (n < 3) return UnitType.LEVEL_ONE_PAINT_TOWER;
        if (n % 3 == 0) return UnitType.LEVEL_ONE_MONEY_TOWER;
        return UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    // PENJELASAN: Menghitung biaya cat per ronde untuk berdiri di petak dengan tipe cat tertentu.
    // Digunakan dalam scoring gerak untuk memilih jalur yang paling hemat cat.
    // - Ally  = 0, gratis, tidak ada penalti (selalu diutamakan saat navigasi)
    // - Empty = 1, biaya sedang
    // - Enemy = 2, paling mahal, sangat dihindari saat cat rendah
    static int paintCostOf(PaintType p) {
        if (p.isAlly()) return 0;
        if (p.isEnemy()) return 2;
        return 1;
    }

    // PENJELASAN: Menghitung jumlah petak cat musuh di 8 arah sekitar lokasi tertentu.
    // Digunakan oleh splasher untuk menentukan arah gerakan menuju area musuh terpadat dalam rangka mereclaim wilayah yang sudah diambil musuh.
    static int countEnemyPaintAround(RobotController rc, MapLocation loc) throws GameActionException {
        int count = 0;
        for (Direction d : directions) {
            MapLocation adj = loc.add(d);
            if (rc.canSenseLocation(adj)) {
                MapInfo info = rc.senseMapInfo(adj);
                if (info.getPaint().isEnemy()) {
                    count++;
                }
            }
        }
        return count;
    }

    // PENJELASAN: Menghitung jumlah petak yang belum diwarnai sekutu di 8 arah sekitar lokasi.
    // Mengukur "frontier richness", seberapa banyak petak baru yang bisa diwarnai jika robot bergerak ke lokasi tersebut. Digunakan dalam scoring gerak soldier sebagai salah satu faktor utama pemilihan arah.
    static int countUnpaintedAround(RobotController rc, MapLocation loc) throws GameActionException {
        int count = 0;
        for (Direction d : directions) {
            MapLocation adj = loc.add(d);
            if (rc.canSenseLocation(adj)) {
                MapInfo info = rc.senseMapInfo(adj);
                if (info.isPassable() && !info.getPaint().isAlly()) {
                    count++;
                }
            }
        }
        return count;
    }

    // PENJELASAN: Menyimpan lokasi saat ini ke dalam circular buffer visited tiles.
    // Buffer berukuran 30 menggunakan pola FIFO, lokasi terlama otomatis terhapus saat buffer penuh dan digantikan lokasi terbaru.
    static void recordVisit(MapLocation loc) {
        visitedTiles[visitedIndex] = loc;
        visitedIndex = (visitedIndex + 1) % visitedTiles.length;
    }

    // PENJELASAN: Memberikan penalti 100 jika lokasi yang dievaluasi ada dalam visited buffer.
    // Penalti besar ini mengurangi skor arah gerak secara signifikan sehingga robot cenderung menghindari lokasi yang baru saja dikunjungi, efektif mencegah robot berputar-putar.
    static int visitedPenalty(MapLocation loc) {
        for (int i = 0; i < visitedTiles.length; i++) {
            if (visitedTiles[i] != null && visitedTiles[i].equals(loc)) {
                return 100;
            }
        }
        return 0;
    }

    // PENJELASAN: Memperbarui daftar tower sekutu yang diketahui setiap giliran.
    // Step 1: Hapus tower dari memori jika lokasinya sudah bisa dilihat tapi tower tidak ada lagi (hancur atau sudah bukan tower), mencegah robot navigasi ke "ghost tower".
    // Step 2: Tambahkan tower baru yang baru pertama kali terlihat ke dalam memori.
    // Sistem ini memungkinkan robot kembali ke tower meskipun sudah keluar dari jangkauan sensor.
    static void rememberTowers(RobotController rc) throws GameActionException {
        for (int i = 0; i < knownTowerCount; i++) {
            if (rc.canSenseLocation(knownTowers[i])) {
                RobotInfo robotThere = rc.senseRobotAtLocation(knownTowers[i]);
                if (robotThere == null || robotThere.team != rc.getTeam()
                        || robotThere.type == UnitType.SOLDIER
                        || robotThere.type == UnitType.MOPPER
                        || robotThere.type == UnitType.SPLASHER) {
                    knownTowers[i] = knownTowers[knownTowerCount - 1];
                    knownTowerCount--;
                    i--;
                }
            }
        }

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.type != UnitType.SOLDIER && ally.type != UnitType.MOPPER && ally.type != UnitType.SPLASHER) {
                boolean known = false;
                for (int i = 0; i < knownTowerCount; i++) {
                    if (knownTowers[i].equals(ally.location)) {
                        known = true;
                        break;
                    }
                }
                if (!known && knownTowerCount < knownTowers.length) {
                    knownTowers[knownTowerCount] = ally.location;
                    knownTowerCount++;
                }
            }
        }
    }

    // PENJELASAN: Mencari tower sekutu terdekat dari daftar knownTowers yang tersimpan di memori.
    // Menggunakan distanceSquared (bukan Euclidean distance) untuk efisiensi komputasi karena tidak memerlukan operasi akar kuadrat. Digunakan robot untuk menentukan tujuan refill cat.
    static MapLocation findNearestKnownTower(RobotController rc) {
        MapLocation nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < knownTowerCount; i++) {
            int dist = rc.getLocation().distanceSquaredTo(knownTowers[i]);
            if (dist < minDist) {
                minDist = dist;
                nearest = knownTowers[i];
            }
        }
        return nearest;
    }

    // PENJELASAN: Navigasi cerdas menuju target dengan mempertimbangkan biaya cat jalur.
    // Fungsi skor yang DIMINIMASI: dist_ke_target + (biaya_cat x 15) + visited_penalty
    // - Lebih dekat ke target, lebih baik (dist kecil lebih dipilih)
    // - Jalur melalui wilayah sekutu (biaya 0), sangat diutamakan
    // - Jalur melalui wilayah musuh (biaya 2 x 15 = 30 ekstra), sangat dihindari
    // Ini memastikan robot selalu memilih rute yang hemat cat saat bepergian ke tujuan.
    static void navigateToward(RobotController rc, MapLocation target) throws GameActionException {
        Direction bestDir = null;
        int bestScore = Integer.MAX_VALUE; // minimize

        for (Direction d : directions) {
            if (!rc.canMove(d)) continue;
            MapLocation next = rc.getLocation().add(d);
            int dist = next.distanceSquaredTo(target);
            int cost = 0;
            if (rc.canSenseLocation(next)) {
                cost = paintCostOf(rc.senseMapInfo(next).getPaint());
            }
            int score = dist + cost * 15 + visitedPenalty(next);
            if (score < bestScore) {
                bestScore = score;
                bestDir = d;
            }
        }

        if (bestDir != null) {
            rc.move(bestDir);
            recordVisit(rc.getLocation());
        }
    }

    // PENJELASAN: Warnai petak di bawah kaki robot jika belum diwarnai oleh sekutu.
    // Dilakukan setiap kali robot berpindah posisi untuk memastikan jalur yang dilalui robot selalu diwarnai, mengurangi penalti cat berdiri di wilayah netral.
    // Try to paint beneath us as we walk to avoid paint penalties.
    // Avoiding wasting paint by re-painting our own tiles.
    static void paintCurrentTile(RobotController rc) throws GameActionException {
        MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
        if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
            rc.attack(rc.getLocation());
        }
    }

    // PENJELASAN: Menghitung jumlah musuh yang akan terkena mop swing ke arah tertentu.
    // Mop swing mengenai 3 petak sekaligus: depan, depan-kiri, depan-kanan dari posisi robot.
    // Fungsi ini dipakai untuk memutuskan apakah mop swing layak dilakukan sebelum menghabiskan cooldown 20 ronde (cooldown besar, jadi harus pastikan target cukup banyak).
    static int countEnemiesInSwingDir(RobotController rc, Direction dir, RobotInfo[] enemies) {
        MapLocation myLoc = rc.getLocation();
        MapLocation front = myLoc.add(dir);
        MapLocation frontLeft = myLoc.add(dir.rotateLeft());
        MapLocation frontRight = myLoc.add(dir.rotateRight());

        int count = 0;
        for (RobotInfo enemy : enemies) {
            MapLocation eLoc = enemy.location;
            if (eLoc.equals(front) || eLoc.equals(frontLeft) || eLoc.equals(frontRight)) {
                count++;
            }
        }
        return count;
    }
}