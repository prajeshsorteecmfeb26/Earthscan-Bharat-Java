/**
 * Utility functions for resolving Indian Cities and Areas
 * using India Post API and OpenStreetMap Nominatim APIs.
 */

// Fetch list of areas / localities for a given City via India Post API
export async function fetchAreasByCity(city) {
    const cleanCity = (city || '').trim();
    if (!cleanCity || cleanCity.length < 2) return [];

    try {
        const res = await fetch(`https://api.postalpincode.in/postoffice/${encodeURIComponent(cleanCity)}`);
        const data = await res.json();
        if (data && data[0] && data[0].Status === 'Success' && data[0].PostOffice) {
            const areaSet = new Set();
            data[0].PostOffice.forEach(po => {
                let name = po.Name.trim();
                if (name) {
                    areaSet.add(name);
                }
            });
            return Array.from(areaSet).sort((a, b) => a.localeCompare(b));
        }
    } catch (err) {
        console.warn('Error fetching areas for city:', err);
    }
    return [];
}

// Fetch details for a given PIN code via India Post API
export async function fetchDetailsByPin(pincode) {
    if (!pincode || !/^\d{6}$/.test(pincode)) {
        return null;
    }
    try {
        const res = await fetch(`https://api.postalpincode.in/pincode/${pincode}`);
        const data = await res.json();
        if (data && data[0] && data[0].Status === 'Success' && data[0].PostOffice) {
            const offices = data[0].PostOffice;
            const primary = offices[0];
            return {
                pincode,
                district: primary.District,
                state: primary.State,
                region: primary.Region,
                postOffices: offices.map(o => o.Name)
            };
        }
    } catch (err) {
        console.warn('Error fetching PIN details:', err);
    }
    return null;
}

// Fetch PIN code by City & Area via India Post API & Nominatim
export async function fetchPinByPlace(city, area) {
    const cleanCity = (city || '').trim();
    const cleanArea = (area || '').trim();
    const searchQuery = cleanArea || cleanCity;

    if (!searchQuery) return null;

    // 1. Try India Post API with Area
    if (cleanArea) {
        try {
            const res = await fetch(`https://api.postalpincode.in/postoffice/${encodeURIComponent(cleanArea)}`);
            const data = await res.json();
            if (data && data[0] && data[0].Status === 'Success' && data[0].PostOffice) {
                // If city is specified, filter post offices matching city/district
                const match = data[0].PostOffice.find(po => 
                    !cleanCity || po.District.toLowerCase().includes(cleanCity.toLowerCase()) || cleanCity.toLowerCase().includes(po.District.toLowerCase())
                ) || data[0].PostOffice[0];
                if (match?.Pincode) return match.Pincode;
            }
        } catch (e) {
            console.warn('India Post office search failed:', e);
        }
    }

    // 2. Try India Post API with City
    if (cleanCity) {
        try {
            const res = await fetch(`https://api.postalpincode.in/postoffice/${encodeURIComponent(cleanCity)}`);
            const data = await res.json();
            if (data && data[0] && data[0].Status === 'Success' && data[0].PostOffice?.length > 0) {
                return data[0].PostOffice[0].Pincode;
            }
        } catch (e) {
            console.warn('India Post city search failed:', e);
        }
    }

    // 3. Fallback to OpenStreetMap Nominatim search
    try {
        const queryStr = cleanArea ? `${cleanArea}, ${cleanCity}, India` : `${cleanCity}, India`;
        const nomUrl = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(queryStr)}&format=json&addressdetails=1&limit=1`;
        const res = await fetch(nomUrl, { headers: { 'User-Agent': 'EarthscanBharatApp/1.0' } });
        const nomData = await res.json();
        if (nomData && nomData[0]?.address?.postcode) {
            return nomData[0].address.postcode;
        }
    } catch (e) {
        console.warn('Nominatim PIN lookup failed:', e);
    }

    return null;
}

// Geocode city name → { lat, lon, displayName, address } via Nominatim (free, no key required)
export async function geocodeCity(query) {
    const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(query)}&format=json&addressdetails=1&limit=1`;
    try {
        const res = await fetch(url, { headers: { 'Accept-Language': 'en', 'User-Agent': 'EarthScanBharat/1.0' } });
        const data = await res.json();
        if (data && data.length > 0) {
            return {
                lat: parseFloat(data[0].lat),
                lon: parseFloat(data[0].lon),
                displayName: data[0].display_name,
                address: data[0].address
            };
        }
    } catch (e) {
        console.warn('Geocoding failed:', e);
    }
    return null;
}

// Multi-tiered PIN code fetcher using accurate web APIs
export async function fetchAccuratePinCode(lat, lon, query = '', addressData = {}) {
    if (addressData?.postcode) {
        return addressData.postcode;
    }

    try {
        const revUrl = `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json&addressdetails=1`;
        const revRes = await fetch(revUrl, { headers: { 'Accept-Language': 'en', 'User-Agent': 'EarthScanBharat/1.0' } });
        const revData = await revRes.json();
        if (revData?.address?.postcode) {
            return revData.address.postcode;
        }
    } catch (e) {
        console.warn('Nominatim reverse lookup failed:', e);
    }

    try {
        const cleanQuery = (query || '').split(',')[0].trim();
        const address = addressData || {};
        const district = (address.state_district || address.county || address.city || address.town || cleanQuery).toLowerCase();
        const state = (address.state || '').toLowerCase();

        if (cleanQuery) {
            const postUrl = `https://api.postalpincode.in/postoffice/${encodeURIComponent(cleanQuery)}`;
            const postRes = await fetch(postUrl);
            const postData = await postRes.json();

            if (postData && postData[0]?.Status === 'Success' && postData[0]?.PostOffice?.length > 0) {
                const offices = postData[0].PostOffice;
                let match = offices.find(po => po.District.toLowerCase() === district);
                if (!match) {
                    match = offices.find(po => po.District.toLowerCase().includes(district) || district.includes(po.District.toLowerCase()));
                }
                if (!match && state) {
                    match = offices.find(po => po.State.toLowerCase() === state);
                }
                if (match?.Pincode) {
                    return match.Pincode;
                }
            }
        }
    } catch (e) {
        console.warn('India Post API lookup failed:', e);
    }

    try {
        const bdcUrl = `https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=${lat}&longitude=${lon}&localityLanguage=en`;
        const bdcRes = await fetch(bdcUrl);
        const bdcData = await bdcRes.json();
        if (bdcData?.postcode) {
            return bdcData.postcode;
        }
    } catch (e) {
        console.warn('BigDataCloud API lookup failed:', e);
    }

    return 'N/A';
}

