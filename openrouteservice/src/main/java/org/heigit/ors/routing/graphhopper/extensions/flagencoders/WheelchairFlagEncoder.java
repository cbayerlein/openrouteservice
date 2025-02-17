/*  This file is part of Openrouteservice.
 *
 *  Openrouteservice is free software; you can redistribute it and/or modify it under the terms of the 
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version 2.1 
 *  of the License, or (at your option) any later version.

 *  This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details.

 *  You should have received a copy of the GNU Lesser General Public License along with this library; 
 *  if not, see <https://www.gnu.org/licenses/>.  
 */
package org.heigit.ors.routing.graphhopper.extensions.flagencoders;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.PriorityCode;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.PMap;

import org.apache.log4j.Logger;
import org.heigit.ors.routing.graphhopper.extensions.ORSOSMReader;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.graphhopper.routing.util.PriorityCode.*;
import org.heigit.ors.routing.graphhopper.extensions.reader.osmfeatureprocessors.OSMAttachedSidewalkProcessor;
import org.heigit.ors.routing.graphhopper.extensions.reader.osmfeatureprocessors.OSMPedestrianProcessor;

public class WheelchairFlagEncoder extends FootFlagEncoder {
    private final boolean debugSkippedWays = false;
    private static final Logger LOGGER = Logger.getLogger(WheelchairFlagEncoder.class.getName());
    public static final int MEAN_SPEED = 4;
    public static final String KEY_WHEELCHAIR = "wheelchair";
    public static final String KEY_FOOTWAY = "footway";
    public static final String KEY_PEDESTRIAN = "pedestrian";
    public static final String KEY_LIVING_STREET = "living_street";
    public static final String KEY_BRIDLEWAY = "bridleway";
    public static final String KEY_SIDEWALK = "sidewalk";
    public static final String KEY_HIGHWAY = "highway";
    public static final String KEY_ROUTE = "route";
    public static final String KEY_BICYCLE = "bicycle";
    public static final String KEY_DESIGNATED = "designated";
    public static final String KEY_OFFICIAL = "official";
    public static final String KEY_CROSSING = "crossing";

    private OSMAttachedSidewalkProcessor osmAttachedSidewalkProcessor = new OSMAttachedSidewalkProcessor();
    private OSMPedestrianProcessor osmPedestrianProcessor = new OSMPedestrianProcessor();

    protected Set<String> acceptedPublicTransport = new HashSet<>(5);
    
    /**
     * Fully suitable for wheelchair users
     */
    private final Set<String> fullyWheelchairAccessibleHighways = new HashSet<>();
    
    /**
     * Suitable for wheelchair users. However highways falling into this category that explicitly indicate a sidewalk is available will be prefered 
     */
    private final Set<String> assumedWheelchairAccessibleHighways = new HashSet<>();
    
    /**
     * Highways that fall into this category will only be considered if further information about surface/smoothness is available
     */
    private final Set<String> limitedWheelchairAccessibleHighways = new HashSet<>();
    
    /**
     * Highways that fall into this category will only be considered if further information about surface/smoothness is available
     */
    private final Set<String> restrictedWheelchairHighways = new HashSet<>();
    
    /**
     * Highways that fall into this category cannot be accessed by Wheelchair users (e.g. steps)
     */
    private final Set<String> nonWheelchairAccessibleHighways = new HashSet<>();

    /**
     * Surfaces that should be avoided
     */
    private final Set<String> problematicSurfaces = new HashSet<>();

    /**
     * Surfaces that are absolutely inaccessible
     */
    private final Set<String> inaccessibleSurfaces = new HashSet<>();

    /**
     * Smoothnesses that are absolutely inaccessible
     */
    private final Set<String> inaccessibleSmoothnesses = new HashSet<>();

    /**
     * Smoothnesses that should be avoided
     */
    private final Set<String> problematicSmoothnesses = new HashSet<>();

    /**
     * preferred Surfaces
     */
    private final Set<String> preferredSurfaces = new HashSet<>();

    /**
     * preferred Smoothnesses
     */
    private final Set<String> preferredSmoothnesses = new HashSet<>();

    /**
     * Tracktypes that are absolutely inaccessible
     */
    private final Set<String> inaccessibleTracktypes = new HashSet<>();

    /**
     * SAC scales that are absolutely inaccessible
     */
    private final Set<String> inaccessibleSacScales = new HashSet<>();

    /**
     * Tracktypes that should be avoided
     */
    private final Set<String> problematicTracktypes = new HashSet<>();

    /**
     * Barriers (nodes) that are not accessible. Routes that would these nodes are not possible.
     */
    private Set<String> inaccessibleBarriers = new HashSet<>(5);
    
    private final Set<String> accessibilityRelatedAttributes = new HashSet<>();

  	public WheelchairFlagEncoder(PMap configuration) {
		 this(configuration.getInt("speed_bits", 4),
			  configuration.getDouble("speed_factor", 1));
    }

    /**
     * Should be only instantiated via EncodingManager
     */
    public WheelchairFlagEncoder() {
        this(4, 1);
    }

    public WheelchairFlagEncoder( int speedBits, double speedFactor ) {
        super(speedBits, speedFactor);
        // test for the following restriction keys
        restrictions.add(KEY_WHEELCHAIR);

        intendedValues.add("limited");


        // http://wiki.openstreetmap.org/wiki/Key:barrier
        // http://taginfo.openstreetmap.org/keys/?key=barrier#values
        absoluteBarriers.add("fence");
        absoluteBarriers.add("wall");
        absoluteBarriers.add("hedge");
        absoluteBarriers.add("retaining_wall");
        absoluteBarriers.add("city_wall");
        absoluteBarriers.add("ditch");
        absoluteBarriers.add("hedge_bank");
        absoluteBarriers.add("guard_rail");
        absoluteBarriers.add("wire_fence");
        absoluteBarriers.add("embankment");

        // specify whether potential barriers block a route if no further information is available
        setBlockByDefault(false);
        
        // http://wiki.openstreetmap.org/wiki/Key:barrier
        // http://taginfo.openstreetmap.org/keys/?key=barrier#values
        // potential barriers do not block, if no further information is available
        potentialBarriers.add("gate");
        potentialBarriers.add("bollard");
        potentialBarriers.add("lift_gate");
        potentialBarriers.add("cycle_barrier");
        potentialBarriers.add("entrance");
        potentialBarriers.add("cattle_grid");
        potentialBarriers.add("swing_gate");
        potentialBarriers.add("chain");
        potentialBarriers.add("bump_gate");

        // add these to absolute barriers
        inaccessibleBarriers.add("stile");
        inaccessibleBarriers.add("block");
        inaccessibleBarriers.add("kissing_gate");
        inaccessibleBarriers.add("turnstile");
        inaccessibleBarriers.add("hampshire_gate");
        
        acceptedPublicTransport.add("platform");
        // acceptedPublicTransport.add("halt"); --> usually describes a building, not a platform
        // acceptedPublicTransport.add("station"); --> usually describes a building, not a platform
        // acceptedPublicTransport.add("subway_entrance");  --> usually describes a sub entrance (building), not a platform
        // acceptedPublicTransport.add("tram_stop"); --> usually describes the stop itself, not the platform
        
        // include funicular, rail, light_rail, subway, narrow_gauge, aerialway=cablecar (tram is already included via AbstractFlagEncoder) with high costs?
        // --> this would be multi-modal routing, which is currently discouraged for ORS
        
        // fully wheelchair accessible, needs double check with tracktype, surface, smoothness
        fullyWheelchairAccessibleHighways.add(KEY_FOOTWAY); // fußweg, separat modelliert
        fullyWheelchairAccessibleHighways.add(KEY_PEDESTRIAN); // fußgängerzone
        fullyWheelchairAccessibleHighways.add(KEY_LIVING_STREET); //spielstraße
        fullyWheelchairAccessibleHighways.add("residential"); // Straße im Wohngebiet
        fullyWheelchairAccessibleHighways.add("unclassified"); // unklassifizierter Fahrweg, meistens schmal
        fullyWheelchairAccessibleHighways.add("service"); // Zufahrtsweg
        fullyWheelchairAccessibleHighways.add("tertiary"); // Kreisstraße
        fullyWheelchairAccessibleHighways.add("tertiary_link"); // Kreisstraßenabfahrt
        fullyWheelchairAccessibleHighways.add("road"); // neue Straße, Klassifizierung bisher unklar

        assumedWheelchairAccessibleHighways.add("trunk"); // Schnellstraße 
        assumedWheelchairAccessibleHighways.add("trunk_link"); // Schnellstraßenabfahrt
        assumedWheelchairAccessibleHighways.add("primary"); // Bundesstraße
        assumedWheelchairAccessibleHighways.add("primary_link"); //Bundessstraßenabfahrt
        assumedWheelchairAccessibleHighways.add("secondary"); // Staatsstraße
        assumedWheelchairAccessibleHighways.add("secondary_link"); // Staatsstraßenabfahrt

        // potentially not suitable for wheelchair users
        limitedWheelchairAccessibleHighways.add("path"); // Wanderweg
        limitedWheelchairAccessibleHighways.add("track"); // Feldweg  
        
        // potentially not allowed for wheelchair users
        restrictedWheelchairHighways.add(KEY_BRIDLEWAY); // disallowed in some countries - needs to be doublechecked with foot=yes
        restrictedWheelchairHighways.add("cycleway"); // disallowed in some countries - needs to be doublechecked with foot=yes or wheelchair=yes
        
        // highways that are not suitable for wheelchair users
        nonWheelchairAccessibleHighways.add("steps"); // Treppen
        
        // attributes to be checked for limited wheelchair accessible highways
        accessibilityRelatedAttributes.add("surface");
        accessibilityRelatedAttributes.add("smoothness");
        accessibilityRelatedAttributes.add("tracktype");
        accessibilityRelatedAttributes.add("incline");
        accessibilityRelatedAttributes.add("sloped_curb");
        accessibilityRelatedAttributes.add("sloped_kerb");

        // inaccessible SAC scales
        inaccessibleSacScales.add("mountain_hiking");
        inaccessibleSacScales.add("demanding_mountain_hiking");
        inaccessibleSacScales.add("alpine_hiking");
        inaccessibleSacScales.add("demanding_alpine_hiking");
        inaccessibleSacScales.add("difficult_alpine_hiking");

        // fill Set of inaccessible Surfaces etc
        problematicSurfaces.add("cobblestone");
        problematicSurfaces.add("unhewn_cobblestone");
        problematicSurfaces.add("sett");
        problematicSurfaces.add("unpaved");
        problematicSurfaces.add("gravel");
        problematicSurfaces.add("compacted");
        problematicSurfaces.add("pebblestone");
        problematicSurfaces.add("grass_paver");
        problematicSurfaces.add("woodchips");
        inaccessibleSurfaces.add("earth");
        inaccessibleSurfaces.add("grass");
        inaccessibleSurfaces.add("dirt");
        inaccessibleSurfaces.add("mud");
        inaccessibleSurfaces.add("sand");
        inaccessibleSurfaces.add("snow");
        inaccessibleSurfaces.add("ice");
        inaccessibleSurfaces.add("salt");
        preferredSurfaces.add("asphalt");
        preferredSurfaces.add("paved");
        problematicSmoothnesses.add("intermediate");
        inaccessibleSmoothnesses.add("bad");
        inaccessibleSmoothnesses.add("very_bad");
        inaccessibleSmoothnesses.add("horrible");
        inaccessibleSmoothnesses.add("very_horrible");
        preferredSmoothnesses.add("excellent");
        problematicTracktypes.add("grade2");
        problematicTracktypes.add("grade3");
        inaccessibleTracktypes.add("grade4");
        inaccessibleTracktypes.add("grade5");

        init();
    }

    @Override
    public double getMeanSpeed() {
        return MEAN_SPEED;
    }
    
    @Override
    public int defineNodeBits(int index, int shift) {
        shift = super.defineNodeBits(index, shift);

        return shift;
    }


    /**
     * Some ways are okay but not separate for pedestrians.
     *
     */
    @Override
    public EncodingManager.Access getAccess(ReaderWay way ) {
    	// check access restrictions
        if (way.hasTag(restrictions, restrictedValues) && !(way.hasTag(restrictions, intendedValues) || way.hasTag(KEY_SIDEWALK, usableSidewalkValues))) {
            if (debugSkippedWays) LOGGER.warn("way skipped (access restrictions): " + way.getId() + " Tags: " + way.getTags().toString());
            return EncodingManager.Access.CAN_SKIP;
        }

    	String highwayValue = way.getTag(KEY_HIGHWAY);
        if (highwayValue == null) {
        	
        	// ferries and shuttle_trains
            if (way.hasTag(KEY_ROUTE, ferries)) {
            	// check whether information on wheelchair accessbility is available
            	if (way.hasTag(KEY_WHEELCHAIR)) {
            		// wheelchair=yes, designated, official, permissive, limited
                	if (way.hasTag(KEY_WHEELCHAIR, intendedValues)) {
                		return EncodingManager.Access.FERRY;
                	}
                	// wheelchair=no, restricted, private
                	if (way.hasTag(KEY_WHEELCHAIR, restrictedValues)) {
                        if (debugSkippedWays) LOGGER.warn("way skipped (no wheelchair ferry): " + way.getId() + " Tags: " + way.getTags().toString());
                		return EncodingManager.Access.CAN_SKIP;
                	}
                }
            	if (way.hasTag("foot")) {
            		// foot=yes, designated, official, permissive, limited
                	if (way.hasTag("foot", intendedValues)) {
                        return EncodingManager.Access.FERRY;
                	}
                	// foot=no, restricted, private
                	if (way.hasTag("foot", restrictedValues)) {
                        if (debugSkippedWays) LOGGER.warn("way skipped (no pedestrian ferry): " + way.getId() + " Tags: " + way.getTags().toString());
                        return EncodingManager.Access.CAN_SKIP;
                	}
            	}
            	return EncodingManager.Access.WAY;
            }
            
            // public transport in general
            // railways (platform, station)
            if (way.hasTag("public_transport", acceptedPublicTransport) || way.hasTag("railway", acceptedPublicTransport)) {
            	// check whether information on wheelchair accessbility is available
            	if (way.hasTag(KEY_WHEELCHAIR)) {
            		// wheelchair=yes, designated, official, permissive, limited
                	if (way.hasTag(KEY_WHEELCHAIR, intendedValues)) {
                		return EncodingManager.Access.WAY;
                	}
                	// wheelchair=no, restricted, private
                	if (way.hasTag(KEY_WHEELCHAIR, restrictedValues)) {
                        if (debugSkippedWays) LOGGER.warn("way skipped (no wheelchair public transport): " + way.getId() + " Tags: " + way.getTags().toString());
                        return EncodingManager.Access.CAN_SKIP;
                	}
                }
            	if (way.hasTag("foot")) {
            		// foot=yes, designated, official, permissive, limited
                	if (way.hasTag("foot", intendedValues)) {
                		return EncodingManager.Access.WAY;
                	}
                	// foot=no, restricted, private
                	if (way.hasTag("foot", restrictedValues)) {
                        if (debugSkippedWays) LOGGER.warn("way skipped (no pedestrian public transport): " + way.getId() + " Tags: " + way.getTags().toString());
                        return EncodingManager.Access.CAN_SKIP;
                	}
            	}
                return EncodingManager.Access.WAY;
            }
            // no highway, no ferry, no railway? --> do not accept way
            if (debugSkippedWays) LOGGER.warn("way skipped (no highway, no ferry, no railway): " + way.getId() + " Tags: " + way.getTags().toString());
            return EncodingManager.Access.CAN_SKIP;
        }
        // highway != null
        else {
            // http://wiki.openstreetmap.org/wiki/DE:Key:sac_scale
            if (way.hasTag("sac_scale", inaccessibleSacScales)) {
                // everything except "hiking" is probably not possible for wheelchair user
                if (debugSkippedWays) LOGGER.warn("way skipped (bad sac scale): " + way.getId() + " Tags: " + way.getTags().toString());
                return EncodingManager.Access.CAN_SKIP;
            }

            if (way.hasTag("surface", inaccessibleSurfaces)) {
                // earth, grass, dirt, mud, sand, snow, ice
                if (debugSkippedWays) LOGGER.warn("way skipped (bad surface): " + way.getId() + " Tags: " + way.getTags().toString());
                return EncodingManager.Access.CAN_SKIP;
            }

            if (way.hasTag("smoothness", inaccessibleSmoothnesses)) {
                // bad aod worse
                if (debugSkippedWays) LOGGER.warn("way skipped (bad smoothness): " + way.getId() + " Tags: " + way.getTags().toString());
                return EncodingManager.Access.CAN_SKIP;
            }

            if (way.hasTag("tracktype", inaccessibleTracktypes)) {
                // grade4 and grade5
                if (debugSkippedWays) LOGGER.warn("way skipped (bad tracktype): " + way.getId() + " Tags: " + way.getTags().toString());
                return EncodingManager.Access.CAN_SKIP;
            }

        	// wheelchair=yes, designated, official, permissive, limited
        	if (way.hasTag(KEY_WHEELCHAIR, intendedValues)) {
        		return EncodingManager.Access.WAY;
        	}
        	// wheelchair=no, restricted, private
        	if (way.hasTag(KEY_WHEELCHAIR, restrictedValues)) {
                if (debugSkippedWays) LOGGER.warn("way skipped (wheelchair no, restricted or private): " + way.getId() + " Tags: " + way.getTags().toString());
        		return EncodingManager.Access.CAN_SKIP;
        	}
        	
        	// do not include nonWheelchairAccessibleHighways
            if (nonWheelchairAccessibleHighways.contains(highwayValue)) {
            	// check for wheelchair accessibility
                if (debugSkippedWays) LOGGER.warn("way skipped (in nonWheelchairAccessibleHighways list): " + way.getId() + " Tags: " + way.getTags().toString());
            	return EncodingManager.Access.CAN_SKIP;
            }
        	
        	// foot=yes, designated, official, permissive, limited
        	if (way.hasTag("foot", intendedValues)) {
        		return EncodingManager.Access.WAY;
        	}
        	
        	// foot=no, restricted, private
        	if (way.hasTag("foot", restrictedValues)) {
                if (debugSkippedWays) LOGGER.warn("way skipped (pedestrian no, restricted or private): " + way.getId() + " Tags: " + way.getTags().toString());
        		return EncodingManager.Access.CAN_SKIP;
        	}
        	
            if (way.hasTag(KEY_SIDEWALK, usableSidewalkValues)) {
            	return EncodingManager.Access.WAY;
            }
            
            // Runge
            if (way.hasTag(KEY_SIDEWALK, noSidewalkValues) && assumedWheelchairAccessibleHighways.contains(highwayValue)) {
                if (debugSkippedWays) LOGGER.warn("way skipped (in assumedWheelchairAccessibleHighways list with no sidewalks): " + way.getId() + " Tags: " + way.getTags().toString());
                return EncodingManager.Access.CAN_SKIP;
            }

            // explicit motorroads are not usable
            if (way.hasTag("motorroad", "yes")) {
                if (debugSkippedWays) LOGGER.warn("way skipped (motorroad): " + way.getId() + " Tags: " + way.getTags().toString());
                return EncodingManager.Access.CAN_SKIP;
            }

            // do not get our feet wet, "yes" is already included above
            if (isBlockFords() && (way.hasTag(KEY_HIGHWAY, "ford") || way.hasTag("ford"))) {
                if (debugSkippedWays) LOGGER.warn("way skipped (ford): " + way.getId() + " Tags: " + way.getTags().toString());
                return EncodingManager.Access.CAN_SKIP;
            }


            if (fullyWheelchairAccessibleHighways.contains(highwayValue) || assumedWheelchairAccessibleHighways.contains(highwayValue) || limitedWheelchairAccessibleHighways.contains(highwayValue)) {
            	// check whether information on wheelchair accessbility is available
            	return EncodingManager.Access.WAY;
            }

            // anything else
            return EncodingManager.Access.WAY;
        }
    }

    @Override
    public long handleRelationTags(long oldRelationFlags, ReaderRelation relation) {
        int code = 0;
        if (relation.hasTag(KEY_ROUTE, "hiking")
                || relation.hasTag(KEY_ROUTE, "foot")
                || relation.hasTag(KEY_ROUTE, "bicycle")
                || relation.hasTag(KEY_ROUTE, "inline_skates")
        ) {
            code = PriorityCode.PREFER.getValue();
        } 
        else if (relation.hasTag(KEY_ROUTE, "ferry")) {
            code = PriorityCode.AVOID_IF_POSSIBLE.getValue();
        }

        int oldCode = (int) relationCodeEncoder.getValue(oldRelationFlags);
        if (oldCode < code)
            return relationCodeEncoder.setValue(0, code);
        return oldRelationFlags;
    }

    //public long handleWayTags(ReaderWay way, long allowed, long relationFlags )

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, EncodingManager.Access access, long relationFlags) {

        if (access.canSkip())
            return edgeFlags;

        if (!access.isFerry()) {
        	// TODO: Depending on availability of sidewalk, surface, smoothness, tracktype and incline MEAN_SPEED or SLOW_SPEED should be encoded
        	// TODO: Maybe also implement AvoidFeaturesWeighting for Wheelchairs
        	
        	// *****************************************  Runge
        	// This is a trick, where we try to underrate the speed for highways that do not have tagged sidewalks.
        	// TODO: this actually affects travel time estimation (might be a good or negative side effect depending on context)
        	double speed = MEAN_SPEED;
        	if (way.hasTag(KEY_HIGHWAY))
        	{
        		
        		String highway = way.getTag(KEY_HIGHWAY);
        		if (assumedWheelchairAccessibleHighways.contains(highway) && !way.hasTag(KEY_SIDEWALK, usableSidewalkValues)) {
                    speed *= 0.8d;
        		}
        		if (fullyWheelchairAccessibleHighways.contains(highway)) {
        			if (highway.equalsIgnoreCase(KEY_FOOTWAY)
                            || highway.equalsIgnoreCase(KEY_PEDESTRIAN)
                            || highway.equalsIgnoreCase(KEY_LIVING_STREET)
                            || highway.equalsIgnoreCase("residential")
                    ) {
        				speed *= 1.25d;
        				if (way.hasTag(KEY_FOOTWAY, KEY_CROSSING) || way.hasTag(KEY_HIGHWAY, KEY_CROSSING)) {
        					speed *= 2d; // should not exceed 10 in total due to encoding restrictions
        				}
        			}
        			// residential, unclassified
        			else if (!way.hasTag(KEY_SIDEWALK, usableSidewalkValues)) {
        				speed *= 0.9d;
        			}
        		}
        		if (restrictedWheelchairHighways.contains(highway) && (way.hasTag("foot", intendedValues) || way.hasTag(KEY_WHEELCHAIR, intendedValues))) {
        			speed *= 1.25d;
    				if (way.hasTag("cycleway", KEY_CROSSING) || way.hasTag(KEY_BRIDLEWAY, KEY_CROSSING) || way.hasTag(KEY_HIGHWAY, KEY_CROSSING)) {
    					speed *= 2d; // should not exceed 10 in total due to encoding restrictions
    				}
        		}
        	}
        	if (way.hasTag("surface", problematicSurfaces)
                    || way.hasTag("smoothness", problematicSmoothnesses)
                    || way.hasTag("tracktype", problematicTracktypes)
            )
        	    speed *= 0.1d;

            if (way.hasTag("surface", preferredSurfaces)
                    || way.hasTag("smoothness", preferredSmoothnesses)
            )
                speed *= 3d;

            if (speed > 10d)
                speed = 10d;

            if (speed < 1d)
                speed = 1d;

            // *****************************************
        	
            speedEncoder.setDecimal(false, edgeFlags, speed);

            accessEnc.setBool(false, edgeFlags, true);
            accessEnc.setBool(true, edgeFlags, true);
            
            int priorityFromRelation = 0;
            if (relationFlags != 0)
                priorityFromRelation = (int) relationCodeEncoder.getValue(relationFlags);

            priorityWayEncoder.setDecimal(false, edgeFlags, PriorityCode.getFactor(handlePriority(way, priorityFromRelation)));
        } 
        else {
            double ferrySpeed = getFerrySpeed(way);
            setSpeed(false, edgeFlags, ferrySpeed);
            accessEnc.setBool(false, edgeFlags, true);
            accessEnc.setBool(true, edgeFlags, true);
        }

        return edgeFlags;
    }

    /**
     * Parse tags on nodes. Node tags can add to speed (like traffic_signals) where the value is
     * strict negative or block access (like a barrier), then the value is strict positive. This
     * method is called in the second parsing step.
     */
    @Override
    public long handleNodeTags(ReaderNode node) {
        long encoded = super.handleNodeTags(node);
        // We want to be more strict with fords, as only if it is declared as wheelchair accessible do we want to cross it
        if (isBlockFords() && (node.hasTag(KEY_HIGHWAY, "ford") || node.hasTag("ford")) && !node.hasTag(KEY_WHEELCHAIR, intendedValues)) {
            encoded = getEncoderBit();
        }
        return encoded;
    }

    @Override
    protected int handlePriority(ReaderWay way, int priorityFromRelation) {
    	int positiveFeatures = 0;
    	int negativeFeatures = 0;
    	int priority;
    	
    	// http://wiki.openstreetmap.org/wiki/DE:Key:traffic_calming
        String highwayValue = way.getTag(KEY_HIGHWAY);
        double maxSpeed = getMaxSpeed(way);
        
        if (maxSpeed > 0) {
        	 if (maxSpeed > 50) {
             	negativeFeatures++;
             	if (maxSpeed > 60) {
             		negativeFeatures++;
             		if (maxSpeed > 80) {
             			negativeFeatures++;
             		}
             	}
             }
             
             if (maxSpeed > 0 && maxSpeed <= 20) {
             	positiveFeatures+=1;
             }
        }

        
        if (assumedWheelchairAccessibleHighways.contains(highwayValue)) {
        	if (highwayValue.equalsIgnoreCase("trunk") || highwayValue.equalsIgnoreCase("trunk_link")) {
        		negativeFeatures+=5;
        	}
        	else if (highwayValue.equalsIgnoreCase("primary") || highwayValue.equalsIgnoreCase("primary_link")) {
        		negativeFeatures+=3;
        	}
        	else { // secondary, tertiary, road, service
        		negativeFeatures+=1;
        	}
        }
        
        // do not rate foot features twice
        boolean isFootEvaluated = false;
        if (fullyWheelchairAccessibleHighways.contains(highwayValue)) {
       		if (highwayValue.equalsIgnoreCase(KEY_FOOTWAY) || highwayValue.equalsIgnoreCase(KEY_PEDESTRIAN) || highwayValue.equalsIgnoreCase(KEY_LIVING_STREET)) {
        		positiveFeatures+=5;
        		isFootEvaluated = true;
        	}
        	else {
        		// residential, unclassified
        		negativeFeatures++;
        	}
        }
        
        if (!isFootEvaluated) {
        	// key=sidewalk
        	if (way.hasTag(KEY_SIDEWALK, usableSidewalkValues)) {
            	positiveFeatures+=5;
            }
        	// key=foot
        	else if (way.hasTag("foot", KEY_DESIGNATED)) {
    			positiveFeatures += 5;
    		}
    		else if (way.hasTag("foot", intendedValues) || way.hasTag(KEY_BICYCLE, KEY_DESIGNATED)) {
    			positiveFeatures += 2;
    		}
        }


        if (!osmAttachedSidewalkProcessor.hasSidewalkInfo(way) && !osmPedestrianProcessor.isPedestrianisedWay(way))
            negativeFeatures+=2;

        int sum = positiveFeatures - negativeFeatures;

        if (sum <= -6) priority = AVOID_AT_ALL_COSTS.getValue();
        else if (sum <= -3) priority = REACH_DEST.getValue();
        else if (sum <= -1) priority = AVOID_IF_POSSIBLE.getValue();
        else if (sum ==0) priority = UNCHANGED.getValue();
        else if (sum <= 2) priority = PREFER.getValue();
        else if (sum <= 5) priority = VERY_NICE.getValue();
        else priority = BEST.getValue();

        return priority;
    }

    @Override
    public String toString()
    {
        return FlagEncoderNames.WHEELCHAIR;
    }

	@Override
	public int getVersion() {
		return 2;
	}

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final WheelchairFlagEncoder other = (WheelchairFlagEncoder) obj;
        return toString().equals(other.toString());
    }

    @Override
    public int hashCode() {
        return ("WheelchairFlagEncoder" + toString()).hashCode();
    }
}
