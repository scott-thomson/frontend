/**
 * Sample Gantt chart for DWP work
 */

var tasks = [
{ "startDate":new Date(2014,05,25),
  "endDate":new Date(2014,05,26,23,59),
  "taskName":"Entitled",
  "status":"ENT"} ,

  { "startDate":new Date(2014,05,27),
  "endDate":new Date(2014,06,12,23,59),
  "taskName":"Entitled",
  "status":"ENT"} ,

{ "startDate":new Date(2014,06,13),
  "endDate":new Date(2014,12,27,23,59),
  "taskName":"Not Entitled",
  "status":"NOT"}

];


var taskStatus = {
    "ENT" : "bar-entitled",
    "NOT" : "bar-not-entitled"
};

var taskNames = [ "Entitled", "Not Entitled" ];

var format = "%a %d %b";

var gantt = d3.gantt().taskTypes(taskNames).taskStatus(taskStatus).tickFormat(format).height(160).width(800);

gantt.timeDomainMode("fit");

gantt(tasks);

var tooltip = d3.select("body").append("div")   
.attr("class", "tooltip")               
.style("opacity", 0);

d3.select(".gantt-chart").selectAll("rect")
.on("mouseover", function(d){
    tooltip.transition()        
           .duration(200)
           .style("opacity", .9);      
    tooltip.html(d.taskName +"<br/>" + "From: " + d.startDate.toDateString() + "<br/>" + "To: " + d.endDate.toDateString() ) //+ "<br/>" + d3.select(this).attr("transform")
           .style("left", (d3.event.pageX) + "px")
           .style("top", (d3.event.pageY - 28) + "px");
    })                  
.on("mouseout", function(d) {       
    tooltip.transition()        
           .duration(500)      
           .style("opacity", 0);   
});

