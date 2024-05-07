select Artist.ArtistId, Artist.Name, count(*) as AlbumCount
  from Artist
  join Album using (ArtistId)
  group by Artist.ArtistId
  order by AlbumCount desc;

select (milliseconds / 60000) as minutes, count(*) as trackcount
  from Track
  group by minutes
  order by minutes asc;

select title, count(*) as employeecount
  from employee
  group by title;

select MediaType.Name as medianame, count(*)
  from MediaType
  join Track using (MediaTypeId)
  group by medianame;

select strftime('%Y', InvoiceDate) as year, sum(Total) as yeartotal
  from Invoice
  group by year;

select albumcount, count(*) as groupcount
  from (select artistid, count(*) as albumcount from album group by artistid)
  group by albumcount;
