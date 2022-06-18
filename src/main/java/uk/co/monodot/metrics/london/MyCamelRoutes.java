package uk.co.monodot.metrics.london;

import org.apache.camel.Exchange;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;

public class MyCamelRoutes extends EndpointRouteBuilder {

    private static int WEATHER_INTERVAL_SECONDS = 300;

    @Override
    public void configure() throws Exception {
        from("timer:mytimer?period=" + (WEATHER_INTERVAL_SECONDS * 1000))
                .id("temperature-route")
                .log("Fetching the weathers my fren")

                // Call the Norwegian weather service to get the forecast
                .to(https("api.met.no/weatherapi/locationforecast/2.0/compact?lat={{weather.lat}}&lon={{weather.lon}}")
                    .advanced().userAgent("{{weather.useragent}}"))
                .unmarshal().json()

                // Let's log out the actual air temperature
                .log("Temp is: ${body[properties][timeseries][0][data][instant][details][air_temperature]}")

                // Build up the content for the push to Graphite
                .transform().groovy("[[ " +
                    "name: 'london_temperature', " +
                    "interval: " + WEATHER_INTERVAL_SECONDS + ", " +
                    "value: body.properties.timeseries[0].data.instant.details.air_temperature.round(), " +
                    "mtype: 'gauge', " +
                    "time: (new Date().getTime() / 1000).round() " +
                    "]]")

                // Convert to JSON for Graphite
                .marshal().json()
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))

                .log("${body}")
                .to(https("{{grafana.graphite.hostname}}/graphite/metrics")
                        .authenticationPreemptive(true)
                        .authMethod("BASIC")
                        .authUsername("{{grafana.username}}")
                        .authPassword("{{grafana.apikey}}"))

                .log("Done!");



    }
}
