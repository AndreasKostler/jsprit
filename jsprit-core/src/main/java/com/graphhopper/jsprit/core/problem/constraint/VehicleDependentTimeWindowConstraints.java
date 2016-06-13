/*******************************************************************************
 * Copyright (C) 2014  Stefan Schroeder
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package com.graphhopper.jsprit.core.problem.constraint;

import com.graphhopper.jsprit.core.algorithm.state.InternalStates;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingActivityCosts;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.misc.JobInsertionContext;
import com.graphhopper.jsprit.core.problem.solution.route.activity.End;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.solution.route.state.RouteAndActivityStateGetter;


/**
 * @author stefan
 */
public class VehicleDependentTimeWindowConstraints implements HardActivityConstraint {

    private RouteAndActivityStateGetter states;

    private VehicleRoutingTransportCosts routingCosts;

    private VehicleRoutingActivityCosts activityCosts;

    private boolean timeWindowIsLatestStart = true;

    public VehicleDependentTimeWindowConstraints(RouteAndActivityStateGetter states, VehicleRoutingTransportCosts routingCosts, VehicleRoutingActivityCosts activityCosts) {
        super();
        this.states = states;
        this.routingCosts = routingCosts;
        this.activityCosts = activityCosts;
    }

    public void setTimeWindowIsLatestStart(boolean timeWindowIsLatestStart) {
        this.timeWindowIsLatestStart = timeWindowIsLatestStart;
    }

    @Override
    public ConstraintsStatus fulfilled(JobInsertionContext iFacts, TourActivity prevAct, TourActivity newAct, TourActivity nextAct, double prevActDepTime) {
        double latestVehicleArrival = iFacts.getNewVehicle().getLatestArrival();

        Double latestArrTimeAtNextAct;
        Location nextActLocation;
        if (nextAct instanceof End) {
            latestArrTimeAtNextAct = latestVehicleArrival;
            nextActLocation = iFacts.getNewVehicle().getEndLocation();
            if (!iFacts.getNewVehicle().isReturnToDepot()) {
                nextActLocation = newAct.getLocation();
            }
        } else {
            latestArrTimeAtNextAct = states.getActivityState(nextAct, iFacts.getNewVehicle(), InternalStates.LATEST_OPERATION_START_TIME, Double.class);
            if (latestArrTimeAtNextAct == null) {//otherwise set it to theoretical_latest_operation_startTime
                latestArrTimeAtNextAct = nextAct.getTheoreticalLatestOperationStartTime();
                if(!timeWindowIsLatestStart){
                    latestArrTimeAtNextAct -= activityCosts.getActivityDuration(nextAct,0,iFacts.getNewDriver(),iFacts.getNewVehicle());
                }
            }
            nextActLocation = nextAct.getLocation();
        }

			/*
             * if latest arrival of vehicle (at its end) is smaller than earliest operation start times of activities,
			 * then vehicle can never conduct activities.
			 *
			 *     |--- vehicle's operation time ---|
			 *                        					|--- prevAct or newAct or nextAct ---|
			 */
        double newAct_theoreticalEarliestOperationStartTime = newAct.getTheoreticalEarliestOperationStartTime();
        double theoreticalEarliestOperationStartTimeAtPrevAct = prevAct.getTheoreticalEarliestOperationStartTime();
        double theoreticalEarliestOperationStartTimeAtNextAct = nextAct.getTheoreticalEarliestOperationStartTime();

        if (latestVehicleArrival < theoreticalEarliestOperationStartTimeAtPrevAct ||
            latestVehicleArrival < newAct_theoreticalEarliestOperationStartTime ||
            latestVehicleArrival < theoreticalEarliestOperationStartTimeAtNextAct) {
            return ConstraintsStatus.NOT_FULFILLED_BREAK;
        }
            /*
             * if the latest operation start-time of new activity is smaller than the earliest start of prev. activity,
			 * then
			 *
			 *                    |--- prevAct ---|
			 *  |--- newAct ---|
			 */
        double theoreticalLatestOperationStartTimeAtNewAct = newAct.getTheoreticalLatestOperationStartTime();
        if(!timeWindowIsLatestStart){
            theoreticalLatestOperationStartTimeAtNewAct -= activityCosts.getActivityDuration(newAct,0,iFacts.getNewDriver(),iFacts.getNewVehicle());
        }
        if (theoreticalLatestOperationStartTimeAtNewAct < theoreticalEarliestOperationStartTimeAtPrevAct) {
            return ConstraintsStatus.NOT_FULFILLED_BREAK;
        }

			/*
             *  |--- prevAct ---|
			 *                                          |- earliest arrival of vehicle
			 *                       |--- nextAct ---|
			 */
        double arrTimeAtNextOnDirectRouteWithNewVehicle = prevActDepTime + routingCosts.getTransportTime(prevAct.getLocation(), nextActLocation, prevActDepTime, iFacts.getNewDriver(), iFacts.getNewVehicle());
//        if(!timeWindowIsLatestStart){
//            arrTimeAtNextOnDirectRouteWithNewVehicle += activityCosts.getActivityCost(nextAct,arrTimeAtNextOnDirectRouteWithNewVehicle,iFacts.getNewDriver(),iFacts.getNewVehicle());
//        }
        if (arrTimeAtNextOnDirectRouteWithNewVehicle > latestArrTimeAtNextAct) {
            return ConstraintsStatus.NOT_FULFILLED_BREAK;
        }

			/*
             *                     |--- newAct ---|
			 *  |--- nextAct ---|
			 */
        double theoreticalLatestOperationStartTimeAtNextAct = nextAct.getTheoreticalLatestOperationStartTime();
        if(!timeWindowIsLatestStart){
            theoreticalLatestOperationStartTimeAtNextAct -= activityCosts.getActivityDuration(nextAct,0,iFacts.getNewDriver(),iFacts.getNewVehicle());
        }
        if (newAct.getTheoreticalEarliestOperationStartTime() > theoreticalLatestOperationStartTimeAtNextAct) {
            return ConstraintsStatus.NOT_FULFILLED;
        }
        //			log.info("check insertion of " + newAct + " between " + prevAct + " and " + nextAct + ". prevActDepTime=" + prevActDepTime);
        double arrTimeAtNewAct = prevActDepTime + routingCosts.getTransportTime(prevAct.getLocation(), newAct.getLocation(), prevActDepTime, iFacts.getNewDriver(), iFacts.getNewVehicle());
        double endTimeAtNewAct = Math.max(arrTimeAtNewAct, newAct.getTheoreticalEarliestOperationStartTime()) + activityCosts.getActivityDuration(newAct, arrTimeAtNewAct,iFacts.getNewDriver(),iFacts.getNewVehicle());
        double potentialLatestArrTimeAtNewAct = latestArrTimeAtNextAct - routingCosts.getBackwardTransportTime(newAct.getLocation(), nextActLocation, latestArrTimeAtNextAct, iFacts.getNewDriver(), iFacts.getNewVehicle());
        if(!timeWindowIsLatestStart) {
            potentialLatestArrTimeAtNewAct -= activityCosts.getActivityDuration(newAct, arrTimeAtNewAct, iFacts.getNewDriver(), iFacts.getNewVehicle());
        }

        double latestArrTimeAtNewAct =
            Math.min(theoreticalLatestOperationStartTimeAtNewAct,potentialLatestArrTimeAtNewAct);


			/*
             *  |--- prevAct ---|
			 *                       		                 |--- vehicle's arrival @newAct
			 *        latest arrival of vehicle @newAct ---|
			 */
        if (arrTimeAtNewAct > latestArrTimeAtNewAct) {
            return ConstraintsStatus.NOT_FULFILLED;
        }

        if (nextAct instanceof End) {
            if (!iFacts.getNewVehicle().isReturnToDepot()) {
                return ConstraintsStatus.FULFILLED;
            }
        }
//			log.info(newAct + " arrTime=" + arrTimeAtNewAct);

        double arrTimeAtNextAct = endTimeAtNewAct + routingCosts.getTransportTime(newAct.getLocation(), nextActLocation, endTimeAtNewAct, iFacts.getNewDriver(), iFacts.getNewVehicle());
//        if(!timeWindowIsLatestStart){
//            arrTimeAtNextAct += activityCosts.getActivityCost(nextAct,arrTimeAtNextAct,iFacts.getNewDriver(),iFacts.getNewVehicle());
//        }
			/*
             *  |--- newAct ---|
			 *                       		                 |--- vehicle's arrival @nextAct
			 *        latest arrival of vehicle @nextAct ---|
			 */
        if (arrTimeAtNextAct > latestArrTimeAtNextAct) {
            return ConstraintsStatus.NOT_FULFILLED;
        }
        return ConstraintsStatus.FULFILLED;
    }
}

