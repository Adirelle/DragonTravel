package eu.phiwa.dragontravel.nms.v1_8_R3;

import eu.phiwa.dragontravel.core.DragonTravelMain;
import eu.phiwa.dragontravel.core.modules.DragonManagement;
import eu.phiwa.dragontravel.core.objects.Flight;
import eu.phiwa.dragontravel.nms.IRyeDragon;
import eu.phiwa.dragontravel.nms.MovementType;
import net.minecraft.server.v1_8_R3.EntityEnderDragon;
import net.minecraft.server.v1_8_R3.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.material.MaterialData;

public class RyeDragon extends EntityEnderDragon implements IRyeDragon {

    private final int scatter = 80;
    private final int wingCooldown = 10;
    private final int travelY = DragonTravelMain.getInstance().getConfigHandler().getTravelHeight();

    private MovementType movementType = MovementType.STATIONARY;
    private Location fromLoc;
    private Location toLoc;
    private Player rider;

    // Flight
    private Flight flight;
    private int currentWaypointIndex;

    //Travel
    private Location midLocA;
    private Location midLocB;
    private boolean finalMove = false;


    private double coveredDist;
    private double totalDist;

    private double XperTick;
    private double YperTick;
    private double ZperTick;

    public RyeDragon(Location loc) {
        this(loc, ((CraftWorld) loc.getWorld()).getHandle());
    }

    public RyeDragon(Location loc, World notchWorld) {
        super(notchWorld);
        setPosition(loc.getX(), loc.getY(), loc.getZ());
        yaw = loc.getYaw() + 180;
        pitch = 0f;
        while (yaw > 360)
            yaw -= 360;
        while (yaw < 0)
            yaw += 360;
        notchWorld.addEntity(this);
    }

    public RyeDragon(World notchWorld) {
        super(notchWorld);
    }

    /**
     * This method is a natural method of the Enderdragon extended by the RyeDragon.
     * It's fired when the dragon moves and fires the travel-method again to keep the dragon flying.
     */
    @Override
    public void m() {
        if (getEntity() != null && rider != null)
            if (getEntity().getPassenger() != null)
                getEntity().setPassenger(rider);

        switch (movementType) {
            case TRAVEL:
                travel();
                break;
            case MANNED_FLIGHT:
                flight();
                break;
            default:
                break;
        }
    }

    /**
     * Controls the dragon
     */
    @Override
    public void flight() {
        if ((int) locX != flight.getWaypoints().get(currentWaypointIndex).getX())
            if (locX < flight.getWaypoints().get(currentWaypointIndex).getX())
                locX += XperTick;
            else
                locX -= XperTick;
        if ((int) locY != flight.getWaypoints().get(currentWaypointIndex).getY())
            if ((int) locY < flight.getWaypoints().get(currentWaypointIndex).getY())
                locY += YperTick;
            else
                locY -= YperTick;
        if ((int) locZ != flight.getWaypoints().get(currentWaypointIndex).getZ())
            if (locZ < flight.getWaypoints().get(currentWaypointIndex).getZ())
                locZ += ZperTick;
            else
                locZ -= ZperTick;

        setCoveredDist(getCoveredDist() + Math.hypot(locX, locZ));
        if (coveredDist > totalDist)
            coveredDist = totalDist;
        ((LivingEntity) getEntity()).setHealth(coveredDist);

        if ((Math.abs((int) locZ - flight.getWaypoints().get(currentWaypointIndex).getZ()) <= 3) && Math.abs((int) locX - flight.getWaypoints().get(currentWaypointIndex).getX()) <= 3 && (Math.abs((int) locY - flight.getWaypoints().get(currentWaypointIndex).getY()) <= 5)) {
            if (currentWaypointIndex == flight.getWaypoints().size() - 1) {
                DragonManagement.removeRiderandDragon(getEntity(), flight.getWaypoints().get(currentWaypointIndex).getAsLocation());
                return;
            }

            this.currentWaypointIndex++;

            this.fromLoc = getEntity().getLocation();
            this.toLoc = flight.getWaypoints().get(currentWaypointIndex).getAsLocation();
            this.yaw = getCorrectYaw(toLoc);

            if (!flight.getWaypoints().get(currentWaypointIndex).getWorldName().equals(this.getEntity().getWorld().getName())) {
                this.teleportTo(flight.getWaypoints().get(currentWaypointIndex).getAsLocation(), true);
                this.currentWaypointIndex++;
            }

            setMoveFlight();
            return;
        }
    }

    /**
     * Sets the x,y,z move for each tick
     */
    @Override
    public void setMoveFlight() {
        double distX = fromLoc.getX() - flight.getWaypoints().get(currentWaypointIndex).getX();
        double distY = fromLoc.getY() - flight.getWaypoints().get(currentWaypointIndex).getY();
        double distZ = fromLoc.getZ() - flight.getWaypoints().get(currentWaypointIndex).getZ();
        double tick = Math.sqrt((distX * distX) + (distY * distY)
                + (distZ * distZ)) / DragonTravelMain.getInstance().getConfigHandler().getSpeed();
        this.yaw = getCorrectYaw(flight.getWaypoints().get(currentWaypointIndex).getAsLocation());
        this.XperTick = Math.abs(distX) / tick;
        this.YperTick = Math.abs(distY) / tick;
        this.ZperTick = Math.abs(distZ) / tick;
    }

    /**
     * Starts the specified flight
     *
     * @param flight Flight to start
     */
    @Override
    public void startFlight(Flight flight) {
        this.flight = flight;
        this.currentWaypointIndex = 0;
        this.movementType = MovementType.MANNED_FLIGHT;

        this.toLoc = flight.getWaypoints().get(currentWaypointIndex).getAsLocation();
        this.fromLoc = getEntity().getLocation();
        this.yaw = getCorrectYaw(toLoc);

        setMoveFlight();
    }

    /**
     * Normal Travel
     */
    @Override
    public void travel() {
        if (getEntity().getPassenger() == null)
            return;

        double myX = locX;
        double myY = locY;
        double myZ = locZ;

        if (finalMove) {
            if ((int) locY > (int) toLoc.getY())
                myY -= DragonTravelMain.getInstance().getConfigHandler().getSpeed();
            else if ((int) locY < (int) toLoc.getY())
                myY += DragonTravelMain.getInstance().getConfigHandler().getSpeed();
            else {
                if (!getEntity().getWorld().getName().equals(toLoc.getWorld().getName())) {
                    this.rider = (Player) getEntity().getPassenger();
                    midLocB.getChunk().load();

                    Bukkit.getScheduler().runTaskLater(DragonTravelMain.getInstance(), () -> {
                        DragonManagement.dismount(rider, true);
                        if (midLocB.getZ() < toLoc.getZ())
                            midLocB.setYaw((float) (-Math.toDegrees(Math.atan((midLocB.getX() - toLoc.getX()) / (midLocB.getZ() - toLoc.getZ())))));
                        else if (midLocB.getZ() > toLoc.getZ())
                            midLocB.setYaw((float) (-Math.toDegrees(Math.atan((midLocB.getX() - toLoc.getX()) / (midLocB.getZ() - toLoc.getZ())))) + 180.0F);
                        rider.teleport(midLocB);
                        if (!DragonManagement.mount(rider, false))
                            return;
                        if (!DragonTravelMain.listofDragonriders.containsKey(rider))
                            return;
                        IRyeDragon dragon = DragonTravelMain.listofDragonriders.get(rider);
                        dragon.startTravel(toLoc, false);
                        getEntity().remove();
                    }, 1L);
                } else {
                    DragonManagement.removeRiderandDragon(getEntity(), true);
                    return;
                }
            }
            setPosition(myX, myY, myZ);
            return;
        }

        if ((int) locY < travelY)
            myY += DragonTravelMain.getInstance().getConfigHandler().getSpeed();

        if (myX < toLoc.getX())
            myX += XperTick;
        else
            myX -= XperTick;

        if (myZ < toLoc.getZ())
            myZ += ZperTick;
        else
            myZ -= ZperTick;

        if ((int) myZ == (int) toLoc.getZ() && ((int) myX == (int) toLoc.getX()
                || (((int) myX == (int) toLoc.getX() + 1 || (int) myX == (int) toLoc.getX() - 1) && ((int) myZ == (int) toLoc.getZ() + 1 || (int) myZ == (int) toLoc.getZ() - 1)))) {
            finalMove = true;
        }
        coveredDist = Math.hypot(getEntity().getLocation().getBlockX() - fromLoc.getBlockX(), getEntity().getLocation().getBlockZ() - fromLoc.getBlockZ());
        if (coveredDist > totalDist)
            coveredDist = totalDist;
        ((LivingEntity) getEntity()).setHealth(coveredDist);
        setPosition(myX, myY, myZ);
    }

    /**
     * Sets the x,z move for each tick
     */
    @Override
    public void setMoveTravel() {
        double dist;
        double distX;
        double distY;
        double distZ;
        if (midLocA != null) {
            dist = fromLoc.distance(midLocA);
            distX = fromLoc.getX() - midLocA.getX();
            distY = fromLoc.getY() - midLocA.getY();
            distZ = fromLoc.getZ() - midLocA.getZ();
            this.yaw = getCorrectYaw(midLocA);
        } else {
            dist = fromLoc.distance(toLoc);
            distX = fromLoc.getX() - toLoc.getX();
            distY = fromLoc.getY() - toLoc.getY();
            distZ = fromLoc.getZ() - toLoc.getZ();
            this.yaw = getCorrectYaw(toLoc);
        }
        double tick = dist / DragonTravelMain.getInstance().getConfigHandler().getSpeed();
        XperTick = Math.abs(distX) / tick;
        ZperTick = Math.abs(distZ) / tick;
        YperTick = Math.abs(distY) / tick;
    }

    /**
     * Starts a travel to the specified location
     *
     * @param destLoc Location to start a travel to
     */
    @Override
    public void startTravel(Location destLoc, boolean interWorld) {
        this.movementType = MovementType.TRAVEL;
        this.rider = (Player) getEntity().getPassenger();
        this.fromLoc = getEntity().getLocation();
        if (interWorld) {
            this.midLocA = new Location(getEntity().getWorld(), locX + 50 + Math.random() * 100, travelY, locZ + 50 + Math.random() * 100);
            this.midLocB = destLoc.clone().add(scatter, scatter, scatter);
            this.yaw = getCorrectYaw(midLocA);
        } else {
            this.toLoc = destLoc;
            this.yaw = getCorrectYaw(toLoc);
        }
        setMoveTravel();
    }

    @Override
    public MovementType getMovementType() {
        return movementType;
    }

    @Override
    public Entity getEntity() {
        if (bukkitEntity != null)
            return bukkitEntity;
        return null;
    }

    /**
     * Gets the correct yaw for this specific path
     */
    public float getCorrectYaw(Location destLoc) {
        float f = getEntity().getLocation().getYaw();

        if (getEntity().getLocation().getBlockZ() > destLoc.getBlockZ())
            f = (float) (-Math.toDegrees(Math.atan((getEntity().getLocation().getBlockX() - destLoc.getBlockX()) / (getEntity().getLocation().getBlockZ() - destLoc.getBlockZ()))));
        else if (getEntity().getLocation().getBlockZ() < destLoc.getBlockZ())
            f = (float) (-Math.toDegrees(Math.atan((getEntity().getLocation().getBlockX() - destLoc.getBlockX()) / (getEntity().getLocation().getBlockZ() - destLoc.getBlockZ())))) + 180.0F;
        this.yaw = f;
        this.setSneaking(true);
        ((LivingEntity) getEntity()).damage(1);
        this.getControllerLook().a(destLoc.getX(), destLoc.getY(), destLoc.getZ(), f, 0f);
        return f;
    }

    public double getCoveredDist() {
        return coveredDist;
    }

    public void setCoveredDist(double coveredDist) {
        this.coveredDist = coveredDist;
    }

    public double getTotalDist() {
        return totalDist;
    }

    public void setTotalDist(double totalDist) {
        this.totalDist = totalDist;
    }

	/*
    public double x_() {
		return 3;
	}
	 */

    public void fixWings() {
        if (rider != null)
            ((LivingEntity) getEntity()).damage(2, rider);
        Bukkit.getScheduler().runTaskLater(DragonTravelMain.getInstance(), () -> {
            if (movementType.equals(MovementType.STATIONARY)) {
                WingFixerTask wfTask = new WingFixerTask();
                wfTask.setId(Bukkit.getScheduler().scheduleSyncRepeatingTask(DragonTravelMain.getInstance(), wfTask, 1L, 21L));
            }
        }, 1L);

    }

    public void setMovementType(MovementType movementType) {
        this.movementType = movementType;
    }

    public int getWingCooldown() {
        return wingCooldown;
    }

    public Player getRider() {
        return rider;
    }

    public void setRider(Player rider) {
        this.rider = rider;
    }

    public Flight getFlight() {
        return flight;
    }

    public void setFlight(Flight flight) {
        this.flight = flight;
    }

    public int getCurrentWaypointIndex() {
        return currentWaypointIndex;
    }

    public void setCurrentWaypointIndex(int currentWaypointIndex) {
        this.currentWaypointIndex = currentWaypointIndex;
    }

    public boolean isFinalMove() {
        return finalMove;
    }

    public void setFinalMove(boolean finalMove) {
        this.finalMove = finalMove;
    }

    public int getTravelY() {
        return travelY;
    }

    public double getXperTick() {
        return XperTick;
    }

    public void setXperTick(double xperTick) {
        XperTick = xperTick;
    }

    public double getYperTick() {
        return YperTick;
    }

    public void setYperTick(double yperTick) {
        YperTick = yperTick;
    }

    public double getZperTick() {
        return ZperTick;
    }

    public void setZperTick(double zperTick) {
        ZperTick = zperTick;
    }

    private class WingFixerTask implements Runnable {

        private int id;
        private int cooldown;

        public void setId(int id) {
            this.id = id;
            this.cooldown = wingCooldown;
        }

        @Override
        public void run() {
            cooldown -= 1;
            if (cooldown <= 0)
                Bukkit.getScheduler().cancelTask(id);
            final Location loc = getEntity().getLocation().add(0, 2, 0);
            final Material m[] = new Material[15];
            final MaterialData md[] = new MaterialData[15];

            int counter = 0;
            for (int y = 0; y <= 2; y++) {
                for (int x = -1; x <= 1; x++) {
                    m[counter] = loc.clone().add(x, -y, 0).getBlock().getType();
                    md[counter] = loc.clone().add(x, -y, 0).getBlock().getState().getData();
                    loc.clone().add(x, -y, 0).getBlock().setType(Material.BARRIER);
                    counter++;
                }
                for (int z = -1; z <= 1; z++) {
                    if (z == 0) continue;
                    m[counter] = loc.clone().add(0, -y, z).getBlock().getType();
                    md[counter] = loc.clone().add(0, -y, z).getBlock().getState().getData();
                    loc.clone().add(0, -y, z).getBlock().setType(Material.BARRIER);
                    counter++;
                }
                if (y == 0) {
                    loc.getBlock().setType(Material.WATER);
                }
                if (y == 1) {
                    loc.clone().add(0, -1, 0).getBlock().setType(Material.AIR);
                }
            }

            Bukkit.getScheduler().runTaskLater(DragonTravelMain.getInstance(), () -> {
                int count = 0;
                for (int y = 0; y <= 2; y++) {
                    for (int x = -1; x <= 1; x++) {
                        loc.clone().add(x, -y, 0).getBlock().setType(m[count]);
                        loc.clone().add(x, -y, 0).getBlock().getState().setData(md[count]);
                        count++;
                    }
                    for (int z = -1; z <= 1; z++) {
                        if (z == 0) continue;
                        loc.clone().add(0, -y, z).getBlock().setType(m[count]);
                        loc.clone().add(0, -y, z).getBlock().getState().setData(md[count]);
                        count++;
                    }
                }
            }, 20L);
        }
    }
}
