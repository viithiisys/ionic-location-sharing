import { Injectable } from '@angular/core';
import { Http, Headers, RequestOptions, Response  } from '@angular/http';
import 'rxjs/add/operator/map';
import 'rxjs/Rx';
import {Observable} from 'rxjs/Observable';

/*
  Generated class for the LocationdetailProvider provider.

  See https://angular.io/docs/ts/latest/guide/dependency-injection.html
  for more info on providers and Angular DI.
*/
@Injectable()
export class LocationdetailProvider {
posts:any;
  constructor(public http: Http) {
  }

   getNearbyplaces(geo:any){
     console.log("geo",geo)
      return this.http.get('https://maps.googleapis.com/maps/api/place/nearbysearch/json?location='+geo+'&radius=50&key=AIzaSyC396edhomZPmbt-kcDLcFoC_3nzrGZwY8')
        .map((res:Response) => res.json());
    } 


}
