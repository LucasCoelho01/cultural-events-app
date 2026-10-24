package com.culturalevents.locations.application;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.culturalevents.locations.domain.City;
import com.culturalevents.locations.domain.CityRepository;

/** Resolve a cidade padrão a partir de {@code app.default-city} (formato {@code uf/slug}). */
@Component
public class DefaultCityResolver {

    private static final Pattern FORMAT = Pattern.compile("^([A-Za-z]{2})/([^/\\s]+)$");

    private final CityRepository cities;
    private final String configured;

    public DefaultCityResolver(CityRepository cities, @Value("${app.default-city:}") String configured) {
        this.cities = cities;
        this.configured = configured;
    }

    public City resolve() {
        Matcher matcher = FORMAT.matcher(configured == null ? "" : configured.trim());
        if (!matcher.matches()) {
            throw new DefaultCityMisconfiguredException(
                    "A propriedade app.default-city está malformada; o formato esperado é uf/slug.");
        }
        String uf = matcher.group(1).toUpperCase(Locale.ROOT);
        String slug = matcher.group(2);
        City city = cities.findByUfAndSlug(uf, slug).orElseThrow(() -> new DefaultCityMisconfiguredException(
                "A cidade padrão configurada em app.default-city não existe."));
        if (!city.isActive()) {
            throw new DefaultCityMisconfiguredException(
                    "A cidade padrão configurada em app.default-city está inativa.");
        }
        return city;
    }
}
