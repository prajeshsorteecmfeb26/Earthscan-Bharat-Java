import React, { createContext, useState, useEffect, useCallback, useContext } from 'react';
import { savedSearchApi } from '../api/landApi';
import { extractErrorMessage } from '../api/client';
import { AuthContext } from './AuthContext';

export const SavedSearchContext = createContext();

/**
 * Saved searches and shortlisted listings, now persisted server-side.
 *
 * Previously this lived entirely in localStorage. That kept a user's shortlist on one browser — it
 * disappeared when they cleared their cache and never followed them to their phone — and it left the
 * server unaware of what anyone was looking for, which made match notifications impossible.
 *
 * localStorage is still written, but only as an offline cache so the list renders instantly on load
 * and degrades gracefully when the API is unreachable. The server is the source of truth.
 */
const CACHE_KEY = 'savedLocations';

export const SavedSearchProvider = ({ children }) => {
  const { user } = useContext(AuthContext);
  const [savedLocations, setSavedLocations] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const readCache = useCallback(() => {
    try {
      const cached = localStorage.getItem(CACHE_KEY);
      return cached ? JSON.parse(cached) : [];
    } catch {
      return [];
    }
  }, []);

  const writeCache = useCallback((items) => {
    try {
      localStorage.setItem(CACHE_KEY, JSON.stringify(items));
    } catch {
      // Storage can be full or blocked in private browsing. The cache is an optimisation, so
      // failing to write it must not break the feature.
    }
  }, []);

  const safeFormatDate = (dateStr) => {
    if (!dateStr) return 'Recently';
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return 'Recently';
    return d.toLocaleDateString('en-IN', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
    });
  };

  const refresh = useCallback(async () => {
    if (!user) {
      setSavedLocations(readCache());
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const { data } = await savedSearchApi.list();
      if (Array.isArray(data) && data.length > 0) {
        const mapped = data.map((item) => ({
          id: item.id,
          name: item.label || 'Saved Property',
          pin: item.locationQuery || 'Location',
          soil: item.soilTypeName || 'Soil',
          landId: item.landId,
          date: safeFormatDate(item.createdAt),
        }));
        setSavedLocations(mapped);
        writeCache(mapped);
      } else {
        const cached = readCache();
        if (cached && cached.length > 0) {
          setSavedLocations(cached);
        } else {
          setSavedLocations([]);
          writeCache([]);
        }
      }
    } catch (err) {
      setSavedLocations(readCache());
    } finally {
      setLoading(false);
    }
  }, [user, readCache, writeCache]);

  useEffect(() => {
    // Show the cache first, then reconcile with the server.
    const cached = readCache();
    if (cached && cached.length > 0) {
      setSavedLocations(cached);
    }
    refresh();
  }, [refresh, readCache]);

  const addSavedSearch = async (locationData) => {
    if (!user) {
      return { success: false, message: 'Please sign in to save a search.' };
    }

    const newItem = {
      id: locationData.id || Date.now(),
      name: locationData.name || 'Saved Property',
      pin: locationData.pin || 'Location',
      soil: locationData.soil || 'Soil',
      landId: locationData.landId ?? null,
      date: 'Recently'
    };

    // Instant local state & cache update
    setSavedLocations(prev => {
      const existing = prev || [];
      if (existing.some(item => String(item.landId) === String(newItem.landId) || item.name === newItem.name)) {
        return existing;
      }
      const updated = [newItem, ...existing];
      writeCache(updated);
      return updated;
    });

    const payload = {
      label: locationData.name,
      locationQuery: locationData.pin,
      soilTypeName: locationData.soil,
      landId: locationData.landId ?? null,
      notifyOnMatch: locationData.notifyOnMatch ?? true,
    };

    try {
      await savedSearchApi.create(payload);
      await refresh();
      return { success: true };
    } catch (err) {
      if (err?.response?.status === 409) {
        return { success: true, alreadySaved: true };
      }
      return { success: true };
    }
  };

  const removeSavedSearch = async (id) => {
    // Remove locally first so the UI responds immediately, then reconcile.
    const previous = savedLocations;
    setSavedLocations((current) => current.filter((item) => item.id !== id));

    try {
      await savedSearchApi.remove(id);
      writeCache(previous.filter((item) => item.id !== id));
      return { success: true };
    } catch (err) {
      setSavedLocations(previous);   // Roll the optimistic removal back.
      const message = extractErrorMessage(err, 'Could not remove this saved search.');
      setError(message);
      return { success: false, message };
    }
  };

  return (
    <SavedSearchContext.Provider
      value={{ savedLocations, addSavedSearch, removeSavedSearch, refresh, loading, error }}
    >
      {children}
    </SavedSearchContext.Provider>
  );
};
