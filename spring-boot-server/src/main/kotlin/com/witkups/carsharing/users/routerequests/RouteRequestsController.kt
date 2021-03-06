package com.witkups.carsharing.users.routerequests

import com.witkups.carsharing.throwOnNotFound
import com.witkups.carsharing.mapTo
import com.witkups.carsharing.security.UserService
import com.witkups.carsharing.users.routerequests.RouteJoinRequest.Status.*
import com.witkups.carsharing.users.routes.RoutePartsRepo
import com.witkups.carsharing.users.routes.RouteRepository
import com.witkups.carsharing.users.routes.RoutesResultMapper
import com.witkups.carsharing.users.user.AppUserService
import com.witkups.carsharing.users.user.SimpleUserView
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("request")
class RouteRequestsController(
  private val routeJoinRequestsRepo: RouteJoinRequestsRepo,
  private val routePartRepo: RoutePartsRepo,
  private val appUserService: AppUserService,
  private val userService: UserService,
  private val routesResultMapper: RoutesResultMapper,
  private val routeRepository: RouteRepository) {

  @PostMapping("join")
  fun joinToRoute(@RequestBody routeJoinRequest: RouteJoinRequestJson) =
    routeJoinRequest.mapTo {
      val route = routeRepository.getOne(routeId!!)
      val applicant = appUserService.getCurrentAppUserReference()
      val requestedParts = requestedRoute.map { routePartRepo.getOne(it) }
      val joinVeto = routesResultMapper.canJoinToRoute(route, applicant, requestedParts)
      if (joinVeto != null) {
        throw RouteJoinRequestReject(joinVeto)
      }
      routeJoinRequestsRepo.save(RouteJoinRequest(
        applicant = applicant,
        status = AWAITING,
        route = route,
        requestedRoute = requestedParts.toMutableSet()
      ))
    }.toView()

  @PostMapping("accept/{requestId}")
  fun acceptRequest(@PathVariable requestId: Long) =
    withRouteRequest(requestId) {
      status = ACCEPTED
      requestedRoute.forEach { it.passengers.add(applicant!!) }
    }

  @PostMapping("reject/{requestId}")
  fun rejectRequest(@PathVariable requestId: Long) =
    withRouteRequest(requestId) {
      status = REJECTED
    }

  private inline fun withRouteRequest(requestId: Long, f: RouteJoinRequest.() -> Unit) {
    val routeJoinRequest = getRouteRequestById(requestId)
    asAuthUser() mapTo {
      val requestedRoute = getRouteRequestById(requestId)
      if (requestedRoute.route!!.driver!!.id == userId) {
        f(routeJoinRequest)
      } else {
        throw IllegalAccessException("You can't reject not your route's join requests")
      }
    }

    routeJoinRequestsRepo.save(routeJoinRequest)
  }

  @GetMapping("route/{routeId}")
  fun getAllRouteRequests(@PathVariable routeId: Long): List<RouteJoinRequestView> {
    val route = routeRepository.findById(routeId)
      .throwOnNotFound("route", routeId)
    asAuthUser().let {
      if (it.userId != route.driver!!.id)
        throw IllegalStateException("Route $routeId does not belongs to user ${it.login}")
    }
    return routeJoinRequestsRepo.findAllByRouteIdAndStatus(routeId, AWAITING).map { it.toView() }
  }

  @GetMapping("all/asDriver")
  fun getAllRequestsAsDriver() =
    asAuthUser() mapTo {
      routeJoinRequestsRepo.findAllByDriverId(userId!!).map { it.toView() }
    }

  @GetMapping("all/asPassenger")
  fun getAllRequestsAsPassenger() =
    asAuthUser() mapTo {
      routeJoinRequestsRepo.findAllByApplicantId(userId!!).map { it.toView() }
    }

  @PostMapping("cancel/{requestId}")
  fun cancelRequest(@PathVariable requestId: Long) =
    asAuthUser() mapTo {
      val requestedRoute = getRouteRequestById(requestId)

      if (requestedRoute.applicant!!.id == userId) {
        requestedRoute.status = CANCELED
      } else {
        throw IllegalAccessException("You can't reject not your route's join requests")
      }
    }

  private fun asAuthUser() = userService.getAuthUser()

  private fun getRouteRequestById(requestId: Long) =
    routeJoinRequestsRepo
      .findById(requestId)
      .throwOnNotFound("route join request", requestId)

  private fun RouteJoinRequest.toView(): RouteJoinRequestView {
    val requestedParts = requestedRoute.sortedBy { it.order!! }
    return RouteJoinRequestView(
      requestId = joinRequestId!!,
      user = applicant!!.mapTo {
        SimpleUserView(
          id = id!!,
          firstName = firstName!!,
          lastName = lastName!!,
          dateOfBirth = dateOfBirth!!,
          phoneNumber = phoneNumber,
          lastLoginDate = user!!.lastLogin!!
        )
      },
      cost = requestedParts.sumByDouble { it.cost!! },
      locations = routesResultMapper.getOrderedVisitedLocationsNames(requestedParts),
      partsIds = requestedParts.map { it.id!! }
    )
  }
}
