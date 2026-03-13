package mainbot;

import battlecode.common.*;
import java.util.Random;

public class RobotPlayer {

    static int turnCount = 0;
    static final Random rng = new Random(6147);

    static final Direction[] directions = {
            Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
            Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST,
    };

    static int[][] mapMemory = new int[61][61];

    static MapLocation[] visitedTiles = new MapLocation[60];
    static int visitedIndex = 0;
    static Direction lastDir = null;

    static MapLocation[] alliedTowers = new MapLocation[30];
    static int towerCount = 0;
    static int spawnCounter = 0;

    static int mapWidth  = -1;
    static int mapHeight = -1;
    static int mapArea   = -1;
    static boolean isSmallMap  = false;
    static boolean isMediumMap = false;
    static MapLocation mapCenter = null;

    static int symmetryType = 0;
    static MapLocation predictedEnemyBase = null;

    static MapLocation sectorTarget = null;

    static int idleTimer = 0;
    static final int IDLE_THRESHOLD = 30;

    static int consecutiveEnemyPaintSeen = 0;

    static MapLocation[] knownRuins = new MapLocation[50];
    static int ruinCount = 0;

    public static void run(RobotController rc) throws GameActionException {
        if (mapArea == -1) {
            mapWidth  = rc.getMapWidth();
            mapHeight = rc.getMapHeight();
            mapArea   = mapWidth * mapHeight;
            isSmallMap  = mapArea < 900;
            isMediumMap = (mapArea >= 900 && mapArea <= 2500);
            mapCenter   = new MapLocation(mapWidth / 2, mapHeight / 2);
        }

        while (true) {
            turnCount += 1;
            try {
                updateMapMemory(rc);
                updateTowerMemory(rc);
                updateKnownRuins(rc);
                detectSymmetry(rc);

                switch (rc.getType()) {
                    case SOLDIER:  runSoldier(rc);  break;
                    case SPLASHER: runSplasher(rc); break;
                    case MOPPER:   runMopper(rc);   break;
                    default:       runTower(rc);    break;
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException"); e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception"); e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    static void updateMapMemory(RobotController rc) throws GameActionException {
        MapInfo[] tiles = rc.senseNearbyMapInfos();
        for (MapInfo tile : tiles) {
            MapLocation loc = tile.getMapLocation();
            if (loc.x < 0 || loc.x >= 61 || loc.y < 0 || loc.y >= 61) continue;

            if (tile.isWall() || tile.hasRuin()) mapMemory[loc.x][loc.y] = 4;
            else if (tile.getPaint().isAlly())   mapMemory[loc.x][loc.y] = 2;
            else if (tile.getPaint().isEnemy())  mapMemory[loc.x][loc.y] = 3;
            else                                 mapMemory[loc.x][loc.y] = 1;
        }
    }

    static void updateTowerMemory(RobotController rc) throws GameActionException {
        for (int i = 0; i < towerCount; i++) {
            if (alliedTowers[i] != null && rc.canSenseLocation(alliedTowers[i])) {
                RobotInfo r = rc.senseRobotAtLocation(alliedTowers[i]);
                if (r == null || r.getTeam() != rc.getTeam() || !r.getType().isTowerType()) {
                    alliedTowers[i] = alliedTowers[towerCount - 1];
                    alliedTowers[towerCount - 1] = null;
                    towerCount--;
                    i--;
                }
            }
        }
        for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
            if (!ally.getType().isTowerType()) continue;
            boolean known = false;
            for (int i = 0; i < towerCount; i++) {
                if (alliedTowers[i] != null && alliedTowers[i].equals(ally.getLocation())) { known = true; break; }
            }
            if (!known && towerCount < 30) alliedTowers[towerCount++] = ally.getLocation();
        }
    }

    static void updateKnownRuins(RobotController rc) throws GameActionException {
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (!tile.hasRuin()) continue;
            MapLocation ruinLoc = tile.getMapLocation();
            boolean known = false;
            for (int i = 0; i < ruinCount; i++) {
                if (knownRuins[i] != null && knownRuins[i].equals(ruinLoc)) { known = true; break; }
            }
            if (!known && ruinCount < 50) knownRuins[ruinCount++] = ruinLoc;
        }
    }

    static void detectSymmetry(RobotController rc) throws GameActionException {
        if (symmetryType != 0 && predictedEnemyBase != null) return;

        if (symmetryType == 0 && turnCount > 5) {
            MapInfo[] tiles = rc.senseNearbyMapInfos();
            boolean canBeRotational  = true;
            boolean canBeHorizontal  = true;
            boolean canBeVertical    = true;

            for (MapInfo tile : tiles) {
                if (!tile.isWall() && !tile.hasRuin()) continue;
                MapLocation loc = tile.getMapLocation();

                MapLocation rotPair = new MapLocation(mapWidth - 1 - loc.x, mapHeight - 1 - loc.y);
                MapLocation hPair   = new MapLocation(loc.x, mapHeight - 1 - loc.y);
                MapLocation vPair   = new MapLocation(mapWidth - 1 - loc.x, loc.y);

                if (rc.canSenseLocation(rotPair)) {
                    MapInfo p = rc.senseMapInfo(rotPair);
                    if (tile.isWall() != p.isWall() || tile.hasRuin() != p.hasRuin()) canBeRotational = false;
                }
                if (rc.canSenseLocation(hPair)) {
                    MapInfo p = rc.senseMapInfo(hPair);
                    if (tile.isWall() != p.isWall() || tile.hasRuin() != p.hasRuin()) canBeHorizontal = false;
                }
                if (rc.canSenseLocation(vPair)) {
                    MapInfo p = rc.senseMapInfo(vPair);
                    if (tile.isWall() != p.isWall() || tile.hasRuin() != p.hasRuin()) canBeVertical = false;
                }
            }

            if (canBeRotational)       symmetryType = 1;
            else if (canBeHorizontal)  symmetryType = 2;
            else if (canBeVertical)    symmetryType = 3;
            else                       symmetryType = 1;
        }

        if (symmetryType != 0 && predictedEnemyBase == null && towerCount > 0) {
            MapLocation myTower = alliedTowers[0];
            if (myTower != null) {
                switch (symmetryType) {
                    case 1: predictedEnemyBase = new MapLocation(mapWidth - 1 - myTower.x, mapHeight - 1 - myTower.y); break;
                    case 2: predictedEnemyBase = new MapLocation(myTower.x, mapHeight - 1 - myTower.y); break;
                    case 3: predictedEnemyBase = new MapLocation(mapWidth - 1 - myTower.x, myTower.y); break;
                    default: predictedEnemyBase = mapCenter;
                }
            }
        }
    }

    static boolean tryReloadPaint(RobotController rc) throws GameActionException {
        int curPaint = rc.getPaint();
        int maxPaint = rc.getType().paintCapacity;

        double pct;
        switch (rc.getType()) {
            case SPLASHER: pct = 0.22; break;
            case SOLDIER:  pct = 0.15; break;
            case MOPPER:   pct = 0.08; break;
            default:       pct = 0.20; break;
        }
        int threshold = (int)(maxPaint * pct);

        boolean towerInRange = false;
        for (MapInfo tile : rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared)) {
            RobotInfo robot = rc.senseRobotAtLocation(tile.getMapLocation());
            if (robot != null && robot.getTeam() != rc.getTeam() && robot.getType().isTowerType()) {
                towerInRange = true; break;
            }
        }

        boolean enemyNear = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent()).length > 0;

        boolean finishingTower = false;
        if (rc.getType() == UnitType.SOLDIER) {
            for (MapInfo tile : rc.senseNearbyMapInfos(8)) {
                if (!tile.hasRuin()) continue;
                RobotInfo r = rc.senseRobotAtLocation(tile.getMapLocation());
                if (r != null) continue;
                int unpainted = 0;
                for (MapInfo pt : rc.senseNearbyMapInfos(tile.getMapLocation(), 8)) {
                    if (pt.getMark() != PaintType.EMPTY && pt.getMark() != pt.getPaint()) unpainted++;
                }
                if (unpainted > 0 && unpainted <= 3 && curPaint > unpainted * 5 + 10) {
                    finishingTower = true; break;
                }
            }
        }

        if (curPaint < threshold && !enemyNear && !towerInRange && !finishingTower) {
            MapLocation tower = findNearestTower(rc);
            if (tower != null) {
                if (rc.getLocation().distanceSquaredTo(tower) <= 2) {
                    if (rc.isActionReady()) rc.transferPaint(tower, -(maxPaint - curPaint));
                } else if (rc.isMovementReady()) {
                    smartMoveTo(rc, tower, true);
                }
                return true;
            }
        }
        return false;
    }

    static void runTower(RobotController rc) throws GameActionException {
        if (rc.canUpgradeTower(rc.getLocation())) {
            rc.upgradeTower(rc.getLocation());
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (rc.isActionReady() && enemies.length > 0) {
            RobotInfo target = null;
            int lowestHP = Integer.MAX_VALUE;
            for (RobotInfo e : enemies) {
                if (rc.canAttack(e.getLocation()) && e.getHealth() < lowestHP) {
                    lowestHP = e.getHealth(); target = e;
                }
            }
            if (target != null) rc.attack(target.getLocation());
        }

        if (rc.getPaint() > 350) {
            for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
                if (ally.getType().isTowerType()) continue;
                int cap = ally.getType().paintCapacity;
                if (ally.getPaintAmount() < cap / 3) {
                    int give = Math.min(rc.getPaint() / 5, cap - ally.getPaintAmount());
                    if (give > 10 && rc.canTransferPaint(ally.getLocation(), give)) {
                        rc.transferPaint(ally.getLocation(), give); break;
                    }
                }
            }
        }

        if (!rc.isActionReady() || rc.getPaint() < 220) return;

        MapInfo[] nearby = rc.senseNearbyMapInfos();
        int enemyPaintNearby = 0;
        for (MapInfo t : nearby) { if (t.getPaint().isEnemy()) enemyPaintNearby++; }

        UnitType toSpawn;
        int round = rc.getRoundNum();

        if (enemyPaintNearby > 12) {
            toSpawn = UnitType.MOPPER;
        } else if (isSmallMap) {
            if (round < 15) toSpawn = UnitType.SOLDIER;
            else {
                int mod = spawnCounter % 10;
                if      (mod < 5) toSpawn = UnitType.SPLASHER;
                else if (mod < 8) toSpawn = UnitType.SOLDIER;
                else              toSpawn = UnitType.MOPPER;
            }
        } else if (isMediumMap) {
            if (round < 50) toSpawn = UnitType.SOLDIER;
            else {
                int mod = spawnCounter % 10;
                if      (mod < 4) toSpawn = UnitType.SPLASHER;
                else if (mod < 8) toSpawn = UnitType.SOLDIER;
                else              toSpawn = UnitType.MOPPER;
            }
        } else {
            if (round < 80) toSpawn = UnitType.SOLDIER;
            else if (round < 250) {
                int mod = spawnCounter % 10;
                if      (mod < 3) toSpawn = UnitType.SPLASHER;
                else if (mod < 8) toSpawn = UnitType.SOLDIER;
                else              toSpawn = UnitType.MOPPER;
            } else {
                int mod = spawnCounter % 10;
                if      (mod < 5) toSpawn = UnitType.SPLASHER;
                else if (mod < 7) toSpawn = UnitType.SOLDIER;
                else              toSpawn = UnitType.MOPPER;
            }
        }

        Direction bestSpawnDir = null;
        int bestSpawnScore = Integer.MIN_VALUE;
        for (Direction d : directions) {
            MapLocation spawnLoc = rc.getLocation().add(d);
            if (!rc.canBuildRobot(toSpawn, spawnLoc)) continue;
            int score = -rc.senseNearbyRobots(spawnLoc, 4, rc.getTeam()).length * 15;
            for (MapInfo t : nearby) {
                if (spawnLoc.distanceSquaredTo(t.getMapLocation()) <= 4 && t.getPaint().isEnemy()) score += 6;
            }
            if (score > bestSpawnScore) { bestSpawnScore = score; bestSpawnDir = d; }
        }
        if (bestSpawnDir != null) {
            rc.buildRobot(toSpawn, rc.getLocation().add(bestSpawnDir));
            spawnCounter++;
        }
    }

    static void runSoldier(RobotController rc) throws GameActionException {
        if (sectorTarget == null) {
            int id = rc.getID();
            int qx, qy;
            switch (id % 4) {
                case 0: qx = mapWidth * 3/4; qy = mapHeight * 3/4; break;
                case 1: qx = mapWidth * 1/4; qy = mapHeight * 3/4; break;
                case 2: qx = mapWidth * 3/4; qy = mapHeight * 1/4; break;
                default:qx = mapWidth * 1/4; qy = mapHeight * 1/4; break;
            }
            sectorTarget = new MapLocation(qx, qy);
        }

        if (tryReloadPaint(rc)) return;

        MapLocation myLoc = rc.getLocation();
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        MapInfo targetRuin = null;
        double bestRuinScore = Double.NEGATIVE_INFINITY;

        for (MapInfo tile : nearbyTiles) {
            if (!tile.hasRuin()) continue;
            MapLocation ruinLoc = tile.getMapLocation();
            RobotInfo ruinBot = rc.senseRobotAtLocation(ruinLoc);
            if (ruinBot != null) continue;

            int distToRuin   = myLoc.distanceSquaredTo(ruinLoc);
            int distToCenter = ruinLoc.distanceSquaredTo(mapCenter);
            int distToEnemy  = predictedEnemyBase != null ? ruinLoc.distanceSquaredTo(predictedEnemyBase) : 0;

            double score = 800.0 / (distToRuin + 1) - distToCenter * 1.2 - distToEnemy * 0.3;
            if (score > bestRuinScore) { bestRuinScore = score; targetRuin = tile; }
        }

        if (targetRuin != null) {
            MapLocation ruinLoc = targetRuin.getMapLocation();
            int distToRuin = myLoc.distanceSquaredTo(ruinLoc);
            UnitType typeToBuild = chooseTowerType(rc);

            if (distToRuin <= 8) {
                if (rc.senseMapInfo(ruinLoc).getMark() == PaintType.EMPTY && rc.canMarkTowerPattern(typeToBuild, ruinLoc)) {
                    rc.markTowerPattern(typeToBuild, ruinLoc);
                }

                if (rc.canCompleteTowerPattern(typeToBuild, ruinLoc)) {
                    rc.completeTowerPattern(typeToBuild, ruinLoc);
                    sectorTarget = null; idleTimer = 0; return;
                }

                if (rc.isActionReady()) {
                    boolean painted = false;
                    for (MapInfo pt : rc.senseNearbyMapInfos(ruinLoc, 8)) {
                        if (pt.getMark() == PaintType.EMPTY || pt.getMark() == pt.getPaint()) continue;
                        if (!rc.canAttack(pt.getMapLocation())) continue;
                        rc.attack(pt.getMapLocation(), pt.getMark() == PaintType.ALLY_SECONDARY);
                        painted = true; break;
                    }
                    if (painted && rc.canCompleteTowerPattern(typeToBuild, ruinLoc)) {
                        rc.completeTowerPattern(typeToBuild, ruinLoc);
                        sectorTarget = null; idleTimer = 0; return;
                    }
                }
                if (rc.isMovementReady() && distToRuin > 2) smartMoveTo(rc, ruinLoc, false);
                idleTimer = 0;
                return;

            } else {
                if (rc.isActionReady()) {
                    for (MapInfo tile : nearbyTiles) {
                        if (!tile.hasRuin()) continue;
                        MapLocation tLoc = tile.getMapLocation();
                        if (rc.senseRobotAtLocation(tLoc) != null) continue;

                        int enemyNearRuin = 0;
                        for (MapInfo adj : rc.senseNearbyMapInfos(tLoc, 12)) {
                            if (adj.getPaint().isEnemy()) enemyNearRuin++;
                        }

                        if (enemyNearRuin > 2 && enemyNearRuin < 10) {
                            for (MapInfo pt : rc.senseNearbyMapInfos(tLoc, 8)) {
                                if (pt.getPaint().isEnemy() && rc.canAttack(pt.getMapLocation())) {
                                    rc.attack(pt.getMapLocation()); break;
                                }
                            }
                        }
                    }
                }
                if (rc.isMovementReady()) smartMoveTo(rc, ruinLoc, false);
                if (rc.isActionReady()) doSoldierAttack(rc, myLoc, nearbyTiles);
                return;
            }
        }

        if (rc.isActionReady() && turnCount > 60) {
            int mx = myLoc.x % 4;
            int my = myLoc.y % 4;
            if (mx == 2 && my == 2) {
                boolean nearRuin = false;
                for (int i = 0; i < ruinCount; i++) {
                    if (knownRuins[i] != null && myLoc.distanceSquaredTo(knownRuins[i]) <= 16) {
                        nearRuin = true; break;
                    }
                }
                if (!nearRuin && rc.canMarkResourcePattern(myLoc)) {
                    rc.markResourcePattern(myLoc);
                }
            }
        }

        if (rc.isActionReady()) {
            doSoldierAttack(rc, myLoc, nearbyTiles);
        }

        if (rc.isMovementReady()) {
            idleTimer++;
            if (idleTimer > IDLE_THRESHOLD) {
                if (predictedEnemyBase != null) {
                    sectorTarget = predictedEnemyBase;
                } else {
                    int newSector = (rc.getID() % 4 + turnCount / 30) % 4;
                    int qx, qy;
                    switch (newSector) {
                        case 0: qx = mapWidth * 3/4; qy = mapHeight * 3/4; break;
                        case 1: qx = mapWidth * 1/4; qy = mapHeight * 3/4; break;
                        case 2: qx = mapWidth * 3/4; qy = mapHeight * 1/4; break;
                        default:qx = mapWidth * 1/4; qy = mapHeight * 1/4; break;
                    }
                    sectorTarget = new MapLocation(qx, qy);
                }
                idleTimer = 0;
            }
            fluidExploreMove(rc, true);
        }
    }

    static void doSoldierAttack(RobotController rc, MapLocation myLoc, MapInfo[] nearbyTiles) throws GameActionException {
        MapLocation best = null;
        double bestScore = -1;

        for (MapInfo tile : rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared)) {
            if (!rc.canAttack(tile.getMapLocation())) continue;
            MapLocation tileLoc = tile.getMapLocation();
            double score = 0;
            int dist = myLoc.distanceSquaredTo(tileLoc);
            if (dist == 0) dist = 1;

            RobotInfo bot = rc.senseRobotAtLocation(tileLoc);
            if (bot != null && bot.getTeam() != rc.getTeam() && bot.getType().isTowerType()) {
                score = 3000.0 / dist;
            } else if (tile.getPaint().isEnemy()) {
                boolean borderingAlly = false;
                for (Direction d : directions) {
                    MapLocation adj = tileLoc.add(d);
                    if (rc.canSenseLocation(adj) && rc.senseMapInfo(adj).getPaint().isAlly()) {
                        borderingAlly = true; break;
                    }
                }
                score = (borderingAlly ? 180.0 : 80.0) / dist;
            } else if (tile.getPaint() == PaintType.EMPTY && tile.isPassable() && !tile.hasRuin()) {
                score = 15.0 / dist;
            }

            if (score > bestScore) { bestScore = score; best = tileLoc; }
        }
        if (best != null && bestScore > 0) rc.attack(best);
    }

    static void runSplasher(RobotController rc) throws GameActionException {
        if (tryReloadPaint(rc)) return;

        MapLocation myLoc = rc.getLocation();

        int enemyVisible = 0;
        for (MapInfo t : rc.senseNearbyMapInfos()) { if (t.getPaint().isEnemy()) enemyVisible++; }
        if (enemyVisible > 4) consecutiveEnemyPaintSeen = Math.min(consecutiveEnemyPaintSeen + 2, 20);
        else                  consecutiveEnemyPaintSeen = Math.max(consecutiveEnemyPaintSeen - 1, 0);
        boolean reclaimMode = consecutiveEnemyPaintSeen >= 5;

        if (rc.isActionReady()) {
            MapLocation bestSplash = null;
            int bestSplashValue = 0;
            double bestScore = Double.NEGATIVE_INFINITY;

            MapInfo[] candidates = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
            for (MapInfo cand : candidates) {
                MapLocation targetLoc = cand.getMapLocation();
                if (!rc.canAttack(targetLoc)) continue;

                int paintValue = 0;
                double score = 0;

                for (MapInfo aoe : rc.senseNearbyMapInfos(targetLoc, 4)) {
                    PaintType p = aoe.getPaint();
                    if (p.isEnemy()) {
                        paintValue += 10;
                        score += reclaimMode ? 15 : 7;
                    } else if (p == PaintType.EMPTY && aoe.isPassable() && !aoe.hasRuin()) {
                        paintValue += 5;
                        score += reclaimMode ? 3 : 9;
                    } else if (p.isAlly() || aoe.isWall()) {
                        score -= 1.5;
                    }
                }

                for (int i = 0; i < ruinCount; i++) {
                    if (knownRuins[i] != null && targetLoc.distanceSquaredTo(knownRuins[i]) <= 8) {
                        RobotInfo rb = rc.senseRobotAtLocation(knownRuins[i]);
                        if (rb == null) score += 8;
                    }
                }

                int dist = myLoc.distanceSquaredTo(targetLoc);
                if (dist == 0) dist = 1;
                score = (score * 7.0) / dist;

                int threshold = reclaimMode ? 50 : 30;
                if (paintValue >= threshold && score > bestScore) {
                    bestScore = score; bestSplash = targetLoc; bestSplashValue = paintValue;
                }
            }

            if (bestSplash == null) {
                for (MapInfo cand : candidates) {
                    MapLocation targetLoc = cand.getMapLocation();
                    if (!rc.canAttack(targetLoc)) continue;
                    int pv = 0;
                    for (MapInfo aoe : rc.senseNearbyMapInfos(targetLoc, 4)) {
                        PaintType p = aoe.getPaint();
                        if (p.isEnemy()) pv += 10;
                        else if (p == PaintType.EMPTY && aoe.isPassable()) pv += 5;
                    }
                    if (pv >= 20) { bestSplash = targetLoc; break; }
                }
            }

            if (bestSplash != null) rc.attack(bestSplash);
        }

        if (rc.isMovementReady()) {
            idleTimer++;
            if (idleTimer > IDLE_THRESHOLD) {
                sectorTarget = (predictedEnemyBase != null) ? predictedEnemyBase : mapCenter;
                idleTimer = 0;
            }
            fluidExploreMove(rc, !reclaimMode);
        }
    }

    static void runMopper(RobotController rc) throws GameActionException {
        if (rc.getPaint() < 12 && tryReloadPaint(rc)) return;

        boolean acted = false;
        MapLocation myLoc = rc.getLocation();
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        if (rc.isActionReady() && enemies.length > 0) {
            Direction bestDir = null;
            int bestHits = 0;
            for (Direction dir : directions) {
                if (!rc.canMopSwing(dir)) continue;
                int hits = 0;
                MapLocation f  = myLoc.add(dir);
                MapLocation fl = myLoc.add(dir.rotateLeft());
                MapLocation fr = myLoc.add(dir.rotateRight());
                for (RobotInfo e : enemies) {
                    MapLocation el = e.getLocation();
                    if (el.equals(f) || el.equals(fl) || el.equals(fr)) hits++;
                }
                if (hits > bestHits) { bestHits = hits; bestDir = dir; }
            }
            if (bestDir != null && bestHits >= 1) { rc.mopSwing(bestDir); acted = true; }
        }

        if (rc.isActionReady() && !acted && rc.getPaint() > 40) {
            RobotInfo bestAlly = null;
            int lowestPaint = Integer.MAX_VALUE;
            for (RobotInfo ally : rc.senseNearbyRobots(2, rc.getTeam())) {
                if (ally.getType().isTowerType()) continue;
                int cap = ally.getType().paintCapacity;
                if (ally.getPaintAmount() < (int)(cap * 0.35) && ally.getPaintAmount() < lowestPaint) {
                    lowestPaint = ally.getPaintAmount(); bestAlly = ally;
                }
            }
            if (bestAlly != null) {
                int give = Math.min(rc.getPaint() - 15, bestAlly.getType().paintCapacity - bestAlly.getPaintAmount());
                if (give > 5 && rc.canTransferPaint(bestAlly.getLocation(), give)) {
                    rc.transferPaint(bestAlly.getLocation(), give); acted = true;
                }
            }
        }

        if (rc.isActionReady() && !acted) {
            MapLocation bestMop = null;
            double bestScore = -1;
            for (MapInfo tile : rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared)) {
                if (!tile.getPaint().isEnemy() || !rc.canAttack(tile.getMapLocation())) continue;
                int dist = myLoc.distanceSquaredTo(tile.getMapLocation());
                if (dist == 0) dist = 1;
                RobotInfo there = rc.senseRobotAtLocation(tile.getMapLocation());
                double score = 10.0 / dist + (there != null && there.getTeam() != rc.getTeam() ? 25 : 0);
                if (score > bestScore) { bestScore = score; bestMop = tile.getMapLocation(); }
            }
            if (bestMop != null) rc.attack(bestMop);
        }

        if (rc.isMovementReady()) fluidExploreMove(rc, false);
    }

    static void smartMoveTo(RobotController rc, MapLocation target, boolean preferAllied) throws GameActionException {
        Direction bestDir = null;
        int minScore = Integer.MAX_VALUE;
        double paintRatio = (double) rc.getPaint() / rc.getType().paintCapacity;

        for (Direction dir : directions) {
            if (!rc.canMove(dir)) continue;
            MapLocation next = rc.getLocation().add(dir);
            if (next.x < 0 || next.x >= 61 || next.y < 0 || next.y >= 61) continue;

            int score = next.distanceSquaredTo(target) * 10;
            int tileType = mapMemory[next.x][next.y];

            if (preferAllied && tileType == 2) score -= 260;

            if (paintRatio < 0.5) {
                if (tileType == 3) score += 320;
                if (tileType == 1) score += 90;
            }

            if (lastDir != null) {
                if (dir == lastDir) score -= 25;
                else if (dir == lastDir.rotateLeft() || dir == lastDir.rotateRight()) score -= 8;
            }

            for (int i = 0; i < visitedTiles.length; i++) {
                if (visitedTiles[i] != null && visitedTiles[i].equals(next)) { score += 4000; break; }
            }

            if (score < minScore) { minScore = score; bestDir = dir; }
        }

        if (bestDir != null) {
            rc.move(bestDir);
            lastDir = bestDir;
            recordVisit(rc.getLocation());
        }
    }

    static void fluidExploreMove(RobotController rc, boolean useCompassSector) throws GameActionException {
        Direction bestDir = null;
        int maxScore = Integer.MIN_VALUE;
        MapLocation myLoc = rc.getLocation();
        double paintRatio = (double) rc.getPaint() / rc.getType().paintCapacity;
        int curType = (myLoc.x >= 0 && myLoc.x < 61 && myLoc.y >= 0 && myLoc.y < 61)
                ? mapMemory[myLoc.x][myLoc.y] : 0;

        boolean enemyNear = rc.senseNearbyRobots(-1, rc.getTeam().opponent()).length > 0;

        for (Direction dir : directions) {
            if (!rc.canMove(dir)) continue;
            MapLocation next = myLoc.add(dir);
            if (next.x < 0 || next.x >= 61 || next.y < 0 || next.y >= 61) continue;

            int nextType = mapMemory[next.x][next.y];
            int score = 0;

            if (curType == 2 && (nextType == 1 || nextType == 3)) score += 750;
            else if (nextType == 1 || nextType == 3) score += 480;
            else if (nextType == 0)  score += 220;
            else if (nextType == 2)  score -= 80;

            if (useCompassSector && sectorTarget != null) {
                int dNow  = myLoc.distanceSquaredTo(sectorTarget);
                int dNext = next.distanceSquaredTo(sectorTarget);
                if (dNext < dNow)      score += 420;
                else if (dNext > dNow) score -= 200;
            }

            if (!useCompassSector && predictedEnemyBase != null) {
                int dNow  = myLoc.distanceSquaredTo(predictedEnemyBase);
                int dNext = next.distanceSquaredTo(predictedEnemyBase);
                if (dNext < dNow) score += 220;
            }

            RobotInfo[] allyNear = rc.senseNearbyRobots(next, 4, rc.getTeam());
            if (enemyNear) score += allyNear.length * 200;
            else           score -= allyNear.length * 140;

            if (nextType == 2) score += 75;

            if (dir == Direction.NORTHEAST || dir == Direction.NORTHWEST ||
                    dir == Direction.SOUTHEAST || dir == Direction.SOUTHWEST) score += 45;

            if (lastDir != null) {
                if (dir == lastDir) score += 100;
                else if (dir == lastDir.rotateLeft() || dir == lastDir.rotateRight()) score += 40;
            }

            if (paintRatio < 0.65 && towerCount > 0) {
                int distHome = Integer.MAX_VALUE;
                for (int i = 0; i < towerCount; i++) {
                    if (alliedTowers[i] != null) {
                        int d = next.distanceSquaredTo(alliedTowers[i]);
                        if (d < distHome) distHome = d;
                    }
                }
                if (distHome != Integer.MAX_VALUE) {
                    int mult = isSmallMap ? 1 : (isMediumMap ? 3 : 5);
                    score -= distHome * mult;
                }
            }

            if (paintRatio < 0.45 && nextType == 3) score -= 320;

            for (int i = 0; i < visitedTiles.length; i++) {
                if (visitedTiles[i] != null && visitedTiles[i].equals(next)) { score -= 2200; break; }
            }

            score += rng.nextInt(55);

            if (score > maxScore) { maxScore = score; bestDir = dir; }
        }

        if (bestDir != null) {
            rc.move(bestDir);
            lastDir = bestDir;
            recordVisit(rc.getLocation());
        }
    }

    static UnitType chooseTowerType(RobotController rc) {
        int n = rc.getNumberTowers();
        if (n < 3) return UnitType.LEVEL_ONE_PAINT_TOWER;
        if (n % 3 == 0) return UnitType.LEVEL_ONE_MONEY_TOWER;
        return UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    static MapLocation findNearestTower(RobotController rc) {
        MapLocation nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < towerCount; i++) {
            if (alliedTowers[i] == null) continue;
            int d = rc.getLocation().distanceSquaredTo(alliedTowers[i]);
            if (d < minDist) { minDist = d; nearest = alliedTowers[i]; }
        }
        return nearest;
    }

    static void recordVisit(MapLocation loc) {
        visitedTiles[visitedIndex] = loc;
        visitedIndex = (visitedIndex + 1) % visitedTiles.length;
    }
}