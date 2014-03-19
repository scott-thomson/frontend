$(function() {
	 
	// Add a date picker
	 $(function() {
		 $("#claimDate").datepicker({ dateFormat: "yy-mm-dd" }).val();		
		 $("#claimDate").datepicker('setDate', new Date());
	 });
	 
	// add a click handler to the load button
    $("#loadXmlButton").click(function(event) {
    	$.get( "/assets/resources/ValidateClaim/" + $("#nino").val() + ".xml" , function( data ) {
    		$( "#claimXml" ).val( data );
    	}, "text");
    })
    
    // add a click handler to the submit button
    $("#submitClaimButton").click(function(event) {
        // make an ajax get request to get the message
        jsRoutes.controllers.ClaimController.submitClaim().ajax({
        	method: "POST",
        	data: {claimDate:$("#claimDate").val() , claimXml: $("#claimXml").val()},
            success: function(data) {
            	console.log(data);
                $("#claimResults").val(data);
                
             // Various formatters.
                var formatNumber = d3.format(",d"),
                  formatChange = d3.format("+,d"),
                  formatDate = d3.time.format("%B %d, %Y"),
                  formatTime = d3.time.format("%I:%M %p");
                var dateParseFormat = d3.time.format("%d:%m:%Y");
                // Cast variables to appropriate types
                data.forEach(function(d, i) {
                	d.index = i;
                	try{
                		d.date = dateParseFormat.parse(d.date); // returns a Date
                	} catch (err) {
                		d.date = new Date();
                	}
                });
                // Create the chart objects
                var barChartAwardsByTime = dc.barChart("#dc-bar-time");
                var dataTable = dc.dataTable("#dc-table-graph");
            	// Run the data through crossfilter
                var awards = crossfilter(data);
                var all = awards.groupAll();
                // Setup dimensions
                var awardsByDate = awards.dimension(function (d) { return d.date; });
                var awardAmountByDate = awardsByDate.group(d3.time.month);
                var firstAward = awardsByDate.bottom(1)[0].date;
                var lastAward = awardsByDate.top(1)[0].date;
                
                
                // Create the visualisations
                barChartAwardsByTime.width(970)
	                .height(80)
	                .dimension(awardsByDate)
	                .group(awardAmountByDate)
	    			.transitionDuration(1500)
	    			.centerBar(true)
	    			.x(d3.time.scale().domain([firstAward, lastAward]))
	    			.round(d3.time.month.round)
	    			.xUnits(d3.time.months)
	    			.elasticY(true);
                
                dataTable.width(800).height(800)
	                .dimension(awardsByDate)
	            	.group(function(d) { return "List of awards"})
	                .size(50)
	                .columns([
	                    function(d) { return d.date; },
	                    function(d) { return d.reason; },
	                    function(d) { return "£" + formatNumber(d.amount); },
	                ])
	                .sortBy(function(d){ return d.date; })
	            	.order(d3.descending);
                
            	dc.renderAll();
                
            }
        })
    })
    
})