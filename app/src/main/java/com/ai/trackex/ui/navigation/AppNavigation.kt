package com.ai.trackex.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ai.trackex.ui.screens.category.CategoryScreen
import com.ai.trackex.ui.screens.detail.DetailScreen
import com.ai.trackex.ui.screens.home.HomeScreen
import com.ai.trackex.ui.screens.review.ReviewScreen
import com.ai.trackex.ui.screens.stats.StatsScreen
import java.net.URLDecoder

private const val ANIM_DURATION = 300

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(ANIM_DURATION)) + fadeIn(tween(ANIM_DURATION))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(ANIM_DURATION)) + fadeOut(tween(ANIM_DURATION))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(ANIM_DURATION)) + fadeIn(tween(ANIM_DURATION))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(ANIM_DURATION)) + fadeOut(tween(ANIM_DURATION))
        }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onImageCaptured = { encodedUri ->
                    navController.navigate(Screen.Review.createRoute(encodedUri))
                },
                onManualEntry = {
                    navController.navigate(Screen.ManualEntry.route)
                },
                onExpenseClick = { expenseId ->
                    navController.navigate(Screen.Detail.createRoute(expenseId))
                },
                onManageCategories = {
                    navController.navigate(Screen.Categories.route)
                },
                onStats = {
                    navController.navigate(Screen.Stats.route)
                }
            )
        }

        composable(Screen.Stats.route) {
            StatsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ManualEntry.route) {
            ReviewScreen(
                imageUri = "",
                onConfirm = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Categories.route) {
            CategoryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Review.route,
            arguments = listOf(navArgument("imageUri") { type = NavType.StringType })
        ) { backStackEntry ->
            val imageUri = URLDecoder.decode(
                backStackEntry.arguments?.getString("imageUri") ?: "",
                "UTF-8"
            )
            ReviewScreen(
                imageUri = imageUri,
                onConfirm = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("expenseId") { type = NavType.LongType })
        ) { backStackEntry ->
            val expenseId = backStackEntry.arguments?.getLong("expenseId") ?: 0L
            DetailScreen(
                expenseId = expenseId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
