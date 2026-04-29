import {Component, OnInit} from '@angular/core';
import {RouterLink} from '@angular/router';
import {CommonModule} from '@angular/common';
import {HttpClient} from '@angular/common/http';

@Component({
  selector: 'app-home',
  imports: [RouterLink, CommonModule],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit {
  userName = '';

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.http.get<{ name: string }>('/api/me').subscribe(me => {
      this.userName = me.name;
    });
  }

}
