package org.observabilitystack.geoip.web;

import java.net.InetAddress;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.observabilitystack.geoip.GeoIpEntry;
import org.observabilitystack.geoip.GeolocationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Provides a Geo Lookup service for IPv4 and IPv6 addresses with the help of
 * DB-IP.
 *
 * @author shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
@RestController
public class GeoIpRestController {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final GeolocationProvider geolocations;

    /**
     * Creates a controller that serves the geolocations from the given provider.
     *
     * @param geolocations
     *                     the geolocation provider.
     */
    @Autowired
    public GeoIpRestController(GeolocationProvider geolocations) {
        this.geolocations = requireNonNull(geolocations);
    }

    @GetMapping({ "/favicon.ico", "/robots.txt" })
    public ResponseEntity<Void> handleKnownNotFounds() {
        return ResponseEntity.notFound().build();
    }

    @CrossOrigin(methods = { RequestMethod.GET, RequestMethod.HEAD }, allowedHeaders = GeoIpEntryHttpHeaders.X_GEOIP_ADDRESS)
    @GetMapping("/")
    public ResponseEntity<Void> handleHeader(
            @RequestHeader(name = GeoIpEntryHttpHeaders.X_GEOIP_ADDRESS, required = false) InetAddress address) {
        if (address != null) {
            Optional<GeoIpEntry> result = geolocations.lookup(address);

            if (result.isPresent()) {
                return ResponseEntity.noContent()
                        .headers(new GeoIpEntryHttpHeaders(result.get()))
                        .build();
            } else {
                return ResponseEntity.noContent().build();
            }
        }

        return handleKnownNotFounds();
    }

    /**
     * Lookup the geolocation information for an ip address.
     */
    @CrossOrigin(methods = RequestMethod.GET)
    @GetMapping("/{address:.+}")
    public ResponseEntity<GeoIpEntry> lookup(@PathVariable("address") InetAddress address) {
        final Optional<GeoIpEntry> entry = geolocations.lookup(address);

        if (entry.isPresent()) {
            return ResponseEntity.ok()
                    .headers(new GeoIpEntryLinkHttpHeaders(address, entry.get()))
                    .body(entry.get());
        } else {
            return ResponseEntity.of(entry);
        }
    }

    /**
     * Lookup the geolocation country information for an ip address.
     */
    @CrossOrigin(methods = RequestMethod.GET)
    @GetMapping("/country/{address:.+}")
    public ResponseEntity<String> lookupForCountry(@PathVariable("address") InetAddress address) {
        final Optional<GeoIpEntry> entry = geolocations.lookup(address);

        if (entry.isPresent()) {
            return ResponseEntity.ok()
                    .headers(new GeoIpEntryLinkHttpHeaders(address, entry.get()))
                    .body(entry.get().getCountryCode());
        } else {
            return ResponseEntity.ok().body("");
        }
    }

    @RequestMapping(path = "/", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, GeoIpEntry>> multiLookup(@RequestBody InetAddress[] ipAddresses) {
        if (ipAddresses.length > 100) {
        	throw new TooManyAddressesException("Only 100 address requests allowed at once");
        }
        
        Map<String, GeoIpEntry> entries = new TreeMap<>();
        for (InetAddress address : ipAddresses) {
			Optional<GeoIpEntry> entry = geolocations.lookup(address);
			if (entry.isPresent()) {
				entries.put(address.getHostAddress(), entry.get());
			}
		}
        return ResponseEntity.ok(entries);
    }
    
    @ExceptionHandler(TooManyAddressesException.class)
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleTooManyAddressesException(TooManyAddressesException e) {
        return e.getMessage();
    }

    @ExceptionHandler(InvalidIpAddressException.class)
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleInvalidIpAddress(Exception e) {
        return e.getMessage();
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleException(Exception e) {
        logger.error(e.getMessage(), e);
        return "We ran into an error: " + e.getMessage();
    }

    /**
     * Initializes data binding.
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(InetAddress.class, new InetAdressPropertyEditor());
    }
}
