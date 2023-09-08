package uk.nhs.england.openehr.util

import org.springframework.http.HttpHeaders
import java.io.IOException
import javax.servlet.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class CorsFilter :Filter {


    @Throws(IOException::class, ServletException::class)
    override fun doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
        val response = res as HttpServletResponse
        val request = req as HttpServletRequest
        if ("OPTIONS" != request.method) {
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
            chain.doFilter(req, res)
        } else {
            //     log.debug("Cors Filter: {}", request.method)
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "POST, PUT, GET, OPTIONS, DELETE")
            response.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600")
            response.setHeader(
                HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                "X-FHIR-Starter,authorization,Prefer,Origin,Accept,X-Requested-With,Content-Type,Access-Control-Request-Method,Access-Control-Request-Headers"
            )
        }
    }

    override fun init(filterConfig: FilterConfig?) {}

    override fun destroy() {}
}
