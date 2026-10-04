package ca.mattmccormick.screenbudget

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ca.mattmccormick.screenbudget.HomeChartSeries.AVERAGE
import ca.mattmccormick.screenbudget.HomeChartSeries.DAILY
import ca.mattmccormick.screenbudget.HomeChartSeries.GOAL
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme

private enum class HomeChartSeries { DAILY, GOAL, AVERAGE }

private data class PlottedSeries(
    val kind: HomeChartSeries,
    val x: List<Int>,
    val y: List<Number>,
)

@Composable
internal fun HomeChart(model: HomeChartModel, modifier: Modifier = Modifier) {
    val series = remember(model) { model.plottedSeries() }
    if (series.isEmpty()) {
        Text("No usage data yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    val colors = MaterialTheme.colorScheme
    val dailyPoint = LineCartesianLayer.Point(
        component = rememberShapeComponent(Fill(colors.primary), CircleShape),
        size = 6.dp,
    )
    val lines = series.map {
        when (it.kind) {
            DAILY -> LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(Fill(colors.primary)),
                pointProvider = LineCartesianLayer.PointProvider.single(dailyPoint),
            )
            GOAL -> LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(Fill(colors.secondary)),
                stroke = LineCartesianLayer.LineStroke.Dashed(
                    thickness = 2.dp,
                    dashLength = 8.dp,
                    gapLength = 4.dp,
                ),
                interpolator = StepInterpolator,
            )
            AVERAGE -> LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(Fill(colors.tertiary)),
                stroke = LineCartesianLayer.LineStroke.Dashed(
                    thickness = 2.dp,
                    cap = StrokeCap.Round,
                    dashLength = 2.dp,
                    gapLength = 4.dp,
                ),
            )
        }
    }
    val vicoModel = remember(series) {
        CartesianChartModel(
            LineCartesianLayerModel.build {
                series.forEach { plotted -> series(plotted.x, plotted.y) }
            },
        )
    }
    val firstBoundary = model.weekBoundaryPositions.first()
    val boundaryPlacer = remember(firstBoundary) {
        HorizontalAxis.ItemPlacer.aligned(
            spacing = { 7 },
            offset = { firstBoundary },
        )
    }

    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(lines),
                    rangeProvider = CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = 41.0),
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(
                    line = null,
                    label = null,
                    tick = null,
                    guideline = rememberLineComponent(Fill(colors.outline.copy(alpha = 0.35f))),
                    itemPlacer = boundaryPlacer,
                ),
            ),
            model = vicoModel,
            modifier = modifier.semantics {
                contentDescription = "Six-week distraction chart"
            },
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false),
        )
    }
}

private fun HomeChartModel.plottedSeries(): List<PlottedSeries> = buildList {
    fun appendSeries(kind: HomeChartSeries, values: List<Number?>) {
        val points = values.mapIndexedNotNull { index, value -> value?.let { index to it } }
        if (points.isNotEmpty()) {
            this@buildList.add(
                PlottedSeries(kind, points.map { it.first }, points.map { it.second }),
            )
        }
    }
    appendSeries(DAILY, dailyMinutes)
    appendSeries(GOAL, goalMinutes)
    appendSeries(AVERAGE, weeklyAverageMinutes)
}

private object StepInterpolator : LineCartesianLayer.Interpolator {
    override fun interpolate(
        context: CartesianDrawingContext,
        path: Path,
        points: List<Offset>,
        visibleIndexRange: IntRange,
    ) {
        val first = points[visibleIndexRange.first]
        path.moveTo(first.x, first.y)
        for (index in (visibleIndexRange.first + 1)..visibleIndexRange.last) {
            val point = points[index]
            path.lineTo(point.x, points[index - 1].y)
            path.lineTo(point.x, point.y)
        }
    }
}
