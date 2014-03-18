$(function() {
	
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
            	alert(data.value);
                $("#claimResults").val(data);
            }
        })
    })
})