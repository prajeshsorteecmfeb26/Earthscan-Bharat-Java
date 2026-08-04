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
