import { Component, Input, OnInit } from "@angular/core";
import { RouteWatcher } from "../route-watcher";
import { Route } from "../route";
import { LocationInput } from "../location-input/location-input";
import { TimeDateInput } from "../visit-date/time-date-input";

@Component({
  selector: "app-creator",
  templateUrl: "./creator.component.html",
  styleUrls: ["./creator.component.scss"]
})
export class CreatorComponent extends RouteWatcher implements OnInit {

  @Input()
  locInputs: LocationInput[];

  @Input()
  dateInputs: TimeDateInput[];

  @Input()
  onSubmit: (route: Route) => Promise<any>;

  constructor() {
    super()
  }

  ngOnInit() {
    this.subscribe();
  }

  protected onChange(route: Route) {
  }

  isValid() {
    return this.locInputs.every(inp => inp.isValid()) && this.dateInputs.every(inp => inp.isValid())
  }


}
