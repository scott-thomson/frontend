// Create some sample gantt chart data

var tasks = new Array(Object());        		

var taskStatus = {
    "ENT" : "bar-entitled",
    "NOT" : "bar"
};

var taskNames = [ "Entitled", "Not Entitled" ];

var gantt = d3.gantt();

var format = "%d %b";

var dateFormat = d3.time.format("%Y-%m-%d");

var dateReviver = function(key, value) {
	if (key=="startDate") {
		return new Date(dateFormat.parse(value));
	} else {
		if (key=="endDate") { 
			if (value == "3999-12-31") { return null } 
			return new Date(d3.time.minute.offset(dateFormat.parse(value),+1439)); // Add 23hrs 59min to each end date/time
		}
	}
	return value;
};

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
        	error: function(err) {console.debug("POST failed:"); console.debug(err);},
        	success: function(data) {
//        		console.log("Web Service call returned : \n"+data);

        		// Parse the JSON using a Reviver function to cleanse date formats and open-ended dates
        		tasks=JSON.parse(data,dateReviver);
        		
        		for (i=0;i<tasks.length;i++) {
       		    	if (tasks[i].wasOk) {tasks[i].taskName="Entitled"} else {tasks[i].taskName="Not Entitled"} 
       		    	tasks[i].status = tasks[i].events[0];
       		    }
        		
//        		console.log("After processing :"+JSON.stringify(tasks));
        		
        		gantt = d3.gantt().taskTypes(taskNames).taskStatus(taskStatus).tickFormat(format).height(160).width(600);

        		// Remove any existing timeline and insert a replacement
        		$("#timeline").empty()
        		gantt(tasks,"#timeline");

    			var tooltip = d3.select("body").append("div")   
        			.attr("class", "tooltip")               
        			.style("opacity", 0);

    			d3.select(".gantt-chart").selectAll("rect")
    			.on("mouseover", function(d){
    				tooltip.transition()
    				.duration(200)
    				.style("opacity", .9);      
    				tooltip.html(d.taskName+" ("+d.status+ ")<br/>" + "From: " + d.startDate.toDateString() + "<br/>" + "To: " + (((d.endDate)==null)?"Infinity":d.endDate.toDateString()) ) //+ "<br/>" + d3.select(this).attr("transform")
    				.style("left", (d3.event.pageX) + "px")
    				.style("top", (d3.event.pageY - 28) + "px");
    			})                  
    			.on("mouseout", function(d) {       
    				tooltip.transition()        
    				.duration(500)      
    				.style("opacity", 0);   
				});
        		
        	} // Close of success
        }); // Close of ajax
    }); // Close of click event
}) // Close of main function
        
