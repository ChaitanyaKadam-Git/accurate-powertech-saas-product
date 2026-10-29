package com.saas.quote;

import java.util.List;

/**
 * Standard text copied from the sample proposal. Each tenant can edit it under Settings.
 * BRIEF may use {capacity}, {location}, {client}. TERMS uses one "Topic | Details" row per line (shown as a table).
 */
final class DefaultSections {
    private DefaultSections() {}

    /** Standard text of every business line, for a new company. */
    static List<FixedSection> forOwner(Long o) {
        List<FixedSection> all = new java.util.ArrayList<>();
        for (BusinessLine l : BusinessLine.values()) all.addAll(forOwner(o, l));
        return all;
    }

    static List<FixedSection> forOwner(Long o, BusinessLine line) {
        List<FixedSection> l = line == BusinessLine.GENSET ? genset(o) : solar(o);
        l.forEach(s -> s.setBusinessLine(line.name()));
        return l;
    }

    private static List<FixedSection> solar(Long o) {
        return new java.util.ArrayList<>(List.of(
            sec(o, "BRIEF", 1, "Project's Brief", """
                The proposed project is a grid connected {capacity} KWp grid tied Solar Photovoltaic (PV) power project to be developed at {location}.
                The project will be developed by installation of solar PV modules, solar string inverters and balance of plant materials to feed power to the utility grid.
                The project will be developed using reputed makes of materials.
                Note: capacity may vary depending on detailed engineering after receipt of the work order."""),
            sec(o, "SYSTEM", 2, "Grid Connected Solar Power Plant - System Description", """
                The Photovoltaic (PV) grid connect system consists mainly of 3 components: the PV array, the module mounting structure and the grid tie inverter. The solar PV array directly converts light energy into electrical energy. The module mounting structure holds the modules in position at a particular tilt depending on site latitude, facing south for optimum incidence of sunlight throughout the day.
                The grid tie inverter converts DC power into AC power, synchronizes with the grid power and exports the same to the grid. The grid tie inverter has inbuilt safety devices to cut off / disconnect the solar PV system from the grid during grid failure or if the grid voltage and frequency go off the acceptable range. Apart from these protections, the grid tie inverter also has an inbuilt data logging feature to monitor system performance, energy generation and fault conditions."""),
            sec(o, "PROFILE", 3, "Company Profile", """
                Established in 1999 and headquartered in Pune, Maharashtra.
                Four verticals: new Genset sales and support, rental power, electrical control panels (HT & LT), and a Solar division.
                Solar division provides turnkey EPC for ground mount and rooftop solar PV projects, plus Annual Maintenance Contracts (AMC).
                We provide turnkey solutions from concept to commissioning, including balance of plant.
                We are committed to quality and safety policies, on-time completion and complete after-sales support.
                A dedicated in-house team of experienced professionals is at your service."""),
            sec(o, "CLIENT_SCOPE", 4, "Client's Scope", """
                Bear additional charges such as load extension (contract demand, sanctioned load, connected load), meter, CT-PT, RMU and transformer changes required by DISCOM, CEIG or the Electrical Inspector.
                Provide water, electricity and internet free of cost during project development and O&M.
                Provide a clear, approachable installation roof area and access for vehicles and machinery.
                Provide secure, lockable storage space during construction.
                Provide internet connectivity (minimum 10 Mbps), router, firewall and a static IP for web monitoring.
                Any change in cable route will be charged at actual per meter.
                Approvals for material and personnel access and movement at the project site.
                All project approvals where direct intervention of the Owner is necessary.
                Any new taxes or duties applied after award of LOI / PO will be in the Client's account.
                MSEDCL load increase deposit and charges."""),
            sec(o, "PAYMENT", 5, "Terms of Payments", """
                First milestone payment - 80%: advance along with confirmed signed work order for the project.
                Second milestone payment - 15%: against material readiness at vendor location / proforma invoice readiness, before dispatch.
                Third milestone payment - 5%: on commissioning and handing over of the complete system."""),
            sec(o, "TERMS", 6, "Terms & Conditions", """
                Price basis | The price includes GST on supply, installation and commissioning of the entire project cost.
                Freight & transit insurance | Inclusive in the commercial offer.
                Packing & forwarding | Inclusive in the commercial offer.
                Bank details | [Add bank name, account number, account name, IFSC code and GST number under Settings > Standard text]
                Delivery schedule | 3-4 weeks from the date of receipt of purchase order, advance and design approvals.
                Installation & commissioning schedule | Solar PV system: 90-100 days from receipt of purchase order, advance and design approvals. Liaisoning and net meter installation: 120-160 days, subject to MSEDCL availability; delays by third parties are not our obligation. Completion in time depends on timely payment as per the agreed payment terms.
                Warranty - Solar PV modules | Manufacturer's standard warranty: 90% minimum performance at the end of year 10 and 80% at the end of year 27, from installation or commissioning, whichever is earlier. Breakage and electrical or mechanical damage due to accidents or unforeseen weather are not covered.
                Warranty - Inverter | Manufacturer's standard warranty terms apply as per the selected string inverter.
                Warranty - BOS | Workmanship guarantee of 1 year from installation or commissioning, whichever is earlier. All equipment carries the original manufacturer's warranty. Warranties are not valid for mishandling, abuse, wear and tear, sabotage or force majeure, and are transferred to the Owner on a back-to-back basis.
                Force majeure & jurisdiction | This proposal and the resulting order are subject to standard force majeure clauses. Any dispute is subject to Pune, Maharashtra jurisdiction only.
                Estimated generation | Figures are indicative, depend on site weather conditions and are not a generation guarantee. The contractor is not responsible for generation loss due to grid unavailability or the system being switched off or under breakdown.
                Quotation validity | 7 days from the date of quotation; after that, written confirmation is required.
                Special note | Load increment charges, additional deposit (if any) and new meter charges are at actual to the customer.""")));
    }

    /** Diesel generator sets (sale, installation and commissioning). Starting text: every company edits it under Settings. */
    private static List<FixedSection> genset(Long o) {
        return new java.util.ArrayList<>(List.of(
            sec(o, "BRIEF", 1, "Offer Brief", """
                We are pleased to submit our offer for supply, installation and commissioning of a {capacity} kVA diesel generator set for {client} at {location}.
                The genset is offered with a CPCB-compliant acoustic enclosure, a brushless alternator, an AMF / manual control panel and a base-mounted fuel tank, as per the bill of material.
                Final rating may vary after load assessment at site."""),
            sec(o, "SYSTEM", 2, "Genset - System Description", """
                The diesel generator set consists of a water / air cooled diesel engine close coupled to a brushless alternator, mounted on a common base frame with anti-vibration mounts.
                The acoustic enclosure is powder coated CRCA sheet steel with fire-retardant acoustic foam, designed to meet CPCB noise and emission norms.
                The control panel monitors engine and alternator parameters, protects the set against faults and, with an AMF panel, starts the genset automatically on mains failure and transfers the load.
                Remote monitoring (IoT) and QR-code based service requests are available on supported models."""),
            sec(o, "PROFILE", 3, "Company Profile", """
                Established in 1999 and headquartered in Pune, Maharashtra. ISO 9001:2015 certified.
                Authorised dealer for diesel generator sets from 2.8 kVA to 2000 kVA, with more than 25,000 satisfied customers across India.
                Four verticals: new genset sales and support, rental power, electrical control panels (HT & LT) and a Solar division.
                Factory-trained manpower for installation, commissioning and after-sales service."""),
            sec(o, "CLIENT_SCOPE", 4, "Client's Scope", """
                Civil foundation as per our drawing, and a clear, approachable location for unloading and placing the genset.
                Power and control cabling from the genset to the client's panel, unless included in our bill of material.
                Earthing pits and earthing strip, unless included in our bill of material.
                Exhaust piping beyond the standard length supplied with the genset.
                First fill of diesel and lubricant top-up during commissioning.
                Electrical Inspector / CEIG approvals and any statutory permissions required at site.
                Crane, hydra and unloading arrangements at site, unless included in our offer."""),
            sec(o, "PAYMENT", 5, "Terms of Payments", """
                30% advance along with the confirmed purchase order.
                70% against proforma invoice, before dispatch.
                Installation and commissioning charges, if any, on completion."""),
            sec(o, "TERMS", 6, "Terms & Conditions", """
                Price basis | Prices are for supply of the genset as per the bill of material. GST and freight as mentioned in the commercial offer.
                Delivery | 2-3 weeks from the date of receipt of purchase order and advance, subject to model availability.
                Installation & commissioning | Within 7 days of readiness of the foundation, cabling and fuel at site.
                Warranty | As per the manufacturer's standard warranty for the engine and alternator, from the date of commissioning or dispatch, whichever applies. Wear and tear, misuse and force majeure are not covered.
                Maintenance contract | Comprehensive maintenance contracts (for example 5 years / 6000 hours) are available on request.
                Compliance | Gensets comply with the applicable CPCB emission and noise norms.
                Bank details | [Add bank name, account number, account name, IFSC code and GST number under Settings > Standard text]
                Force majeure & jurisdiction | This offer and the resulting order are subject to standard force majeure clauses. Any dispute is subject to Pune, Maharashtra jurisdiction only.
                Quotation validity | 15 days from the date of quotation; after that, written confirmation is required."""),
            sec(o, "WARRANTY_SERVICE", 7, "Service Support", """
                Free service visits as per the manufacturer's schedule during the warranty period.
                Service support from our trained service engineers across Maharashtra.
                Genuine spares and lubricants available on request.""")));
    }

    private static FixedSection sec(Long owner, String key, int pos, String title, String content) {
        FixedSection s = new FixedSection();
        s.setOwnerId(owner);
        s.setSectionKey(key);
        s.setPosition(pos);
        s.setTitle(title);
        s.setContent(content);
        return s;
    }
}
