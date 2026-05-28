import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';

interface UserProfile {
  sub: string;
  name: string;
  email: string;
  groups: string[];
}

@Component({
  selector: 'app-home',
  imports: [RouterLink, CommonModule],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit {
  profile: UserProfile | null = null;
  profileError = false;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.http.get<UserProfile>('/api/me').subscribe({
      next: (me) => this.profile = me,
      error: () => this.profileError = true,
    });
  }
}
