package ca.mattmccormick.screenbudget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import ca.mattmccormick.screenbudget.HomeChartSeries.AVERAGE
import ca.mattmccormick.screenbudget.HomeChartSeries.DAILY
import ca.mattmccormick.screenbudget.HomeChartSeries.GOAL
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
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

private enum class HomeChartSeries(val label: String, val color: Color) {
    DAILY("Usage", Color(0xFF1F77B4)),
    GOAL("Daily Goal", Color(0xFFFF7F0E)),
    AVERAGE("Weekly Average", Color(0xFF008000)),
}

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
        component = rememberShapeComponent(Fill(DAILY.color), CircleShape),
        size = 6.dp,
    )
    val lines = series.map {
        when (it.kind) {
            DAILY -> LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(Fill(DAILY.color)),
                pointProvider = LineCartesianLayer.PointProvider.single(dailyPoint),
            )
            GOAL -> LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(Fill(GOAL.color)),
                stroke = LineCartesianLayer.LineStroke.Dashed(
                    thickness = 2.dp,
                    dashLength = 8.dp,
                    gapLength = 4.dp,
                ),
                interpolator = StepInterpolator,
            )
            AVERAGE -> LineCartesianLayer.rememberLine(
                fill = LineCartesianLayer.LineFill.single(Fill(AVERAGE.color)),
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

    val dateFormatter = remember(model.dates) {
        val format = DateTimeFormatter.ofPattern("MMM d")
        CartesianValueFormatter { _, value, _ ->
            model.dates.getOrNull(value.toInt())?.format(format).orEmpty()
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            series.forEach { ChartLegendItem(it.kind) }
        }
        ProvideVicoTheme(rememberM3VicoTheme()) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(lines),
                        rangeProvider = CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = 41.0, minY = 0.0),
                    ),
                    startAxis = VerticalAxis.rememberStart(
                        title = { "Minutes" },
                        titleComponent = rememberAxisLabelComponent(),
                        guideline = rememberLineComponent(Fill(colors.outline.copy(alpha = 0.25f))),
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        label = rememberAxisLabelComponent(
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = colors.onSurfaceVariant, fontSize = 10.sp,
                            ),
                        ),
                        valueFormatter = dateFormatter,
                        labelRotationDegrees = -90f,
                        title = { "Date" },
                        titleComponent = rememberAxisLabelComponent(),
                        guideline = rememberLineComponent(Fill(colors.outline.copy(alpha = 0.35f))),
                        itemPlacer = boundaryPlacer,
                    ),
                ),
                model = vicoModel,
                modifier = Modifier.fillMaxWidth().height(240.dp).semantics {
                    contentDescription = "Six-week distraction chart"
                },
                scrollState = rememberVicoScrollState(scrollEnabled = false),
                // Disabling gestures does not change the default zoom; fit all 42 days explicitly.
                zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
            )
        }
    }
}

@Composable
private fun ChartLegendItem(series: HomeChartSeries) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(22.dp, 12.dp)) {
            val effect = when (series) {
                DAILY -> null
                GOAL -> PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 3.dp.toPx()))
                AVERAGE -> PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))
            }
            drawLine(
                series.color, Offset(0f, center.y), Offset(size.width, center.y),
                strokeWidth = 2.dp.toPx(), pathEffect = effect,
            )
            if (series == DAILY) drawCircle(series.color, 3.dp.toPx(), center)
        }
        Text(series.label, style = MaterialTheme.typography.labelSmall)
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
