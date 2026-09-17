package com.eip.backend.dto.search;

import java.time.ZonedDateTime;

/** One of the current user's recent searches. {@code mode} is the search mode it ran in. */
public record SearchActivity(String query, String mode, int resultCount, ZonedDateTime searchedAt) {
}
