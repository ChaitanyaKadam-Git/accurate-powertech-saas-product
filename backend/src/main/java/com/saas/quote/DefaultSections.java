package com.saas.quote;

import java.util.List;

/**
 * Standard text copied from the sample proposal. Each tenant can edit it under Settings.
 * BRIEF may use {capacity}, {location}, {client}. TERMS uses one "Topic | Details" row per line (shown as a table).
 */
final class DefaultSections {
    private DefaultSections() {}

    static List<FixedSection> forOwner(Long o) {
        return List.of(
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
                Special note | Load increment charges, additional deposit (if any) and new meter charges are at actual to the customer."""));
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
